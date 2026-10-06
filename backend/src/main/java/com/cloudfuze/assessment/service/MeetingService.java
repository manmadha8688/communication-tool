package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.AttemptStatus;
import com.cloudfuze.assessment.domain.Part;
import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.entity.Answer;
import com.cloudfuze.assessment.entity.AppUser;
import com.cloudfuze.assessment.entity.Attempt;
import com.cloudfuze.assessment.entity.AttemptPart;
import com.cloudfuze.assessment.entity.Question;
import com.cloudfuze.assessment.exception.ApiException;
import com.cloudfuze.assessment.repository.AnswerRepository;
import com.cloudfuze.assessment.repository.AttemptPartRepository;
import com.cloudfuze.assessment.repository.AttemptRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Part 3: a live meeting. The AI plays the other participant -- the customer, stakeholder or
 * colleague the Meet scenario describes -- and the candidate leads the conversation.
 *
 * <p>THE AI KNOWS THE CHECKLIST AND NEVER SAYS SO. It is given the question's own points to cover
 * as private notes and uses them only to decide what to press on next, one question at a time,
 * until it is convinced or the meeting runs out of replies. The candidate never sees the points.
 *
 * <p>The AI calls happen outside any transaction, so a slow reply never holds the attempt locked.
 */
@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    /** The candidate gets this many replies at most; the AI closes the meeting after the last. */
    public static final int MAX_REPLIES = 12;
    /** The AI may not close the meeting before the candidate has spoken this many times. */
    public static final int MIN_REPLIES = 5;
    private static final String VOICE = "coral";

    private final AttemptRepository attempts;
    private final AttemptPartRepository parts;
    private final AnswerRepository answers;
    private final OpenAiClient ai;
    private final TransactionTemplate tx;
    private final String model;
    private final String liveModel;
    private final Duration grace;
    private final ObjectMapper json = new ObjectMapper();

    public MeetingService(AttemptRepository attempts, AttemptPartRepository parts, AnswerRepository answers,
                          OpenAiClient ai, TransactionTemplate tx,
                          @Value("${app.openai.scoring-model:gpt-4o}") String model,
                          @Value("${app.openai.meeting-model:gpt-realtime}") String liveModel,
                          @Value("${app.test.grace-seconds:20}") int graceSeconds) {
        this.attempts = attempts;
        this.parts = parts;
        this.answers = answers;
        this.ai = ai;
        this.tx = tx;
        this.model = model;
        this.liveModel = liveModel;
        this.grace = Duration.ofSeconds(graceSeconds);
    }

    private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(8);

    /** Runs an AI call with a hard limit, so a slow response can never hang the candidate's screen. */
    private static <T> T within(int seconds, String what, java.util.concurrent.Callable<T> call) throws Exception {
        java.util.concurrent.Future<T> f = POOL.submit(call);
        try {
            return f.get(seconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            f.cancel(true);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, what);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    /** Spoken lines already made, by answer and turn number. Small, and only for the live test. */
    private final java.util.Map<String, byte[]> voices = new java.util.concurrent.ConcurrentHashMap<>();

    /** Everything one call needs, read under the attempt lock and then released. */
    private record Ctx(Long answerId, String title, String scenario, String task, List<String> points,
                       List<Dtos.Turn> turns, boolean ended, Boolean satisfied) {
        int replies() {
            return (int) turns.stream().filter(t -> t.role().equals("YOU")).count();
        }
    }

    // ------------------------------------------------------------------ public calls

    public Dtos.MeetingState state(AppUser user, String key) {
        return view(load(user, key, false), null);
    }

    /** Opens the meeting: the AI's first line, spoken. Safe to call twice; the second returns the same. */
    public Dtos.MeetingState start(AppUser user, String key) throws Exception {
        Ctx c = load(user, key, true);
        if (!c.turns().isEmpty()) {
            return view(c, null);
        }
        JsonNode r = within(45, "The meeting could not start in time. Please press Start again.",
                () -> ai.chat(persona(c), List.of(Map.of("role", "user",
                        "content", "(The meeting starts now. Open it in character, as the situation describes.)")), model, 0.7));
        String say = clean(r.path("say").asText(""));
        if (say.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The meeting could not start. Please try again.");
        }
        List<Dtos.Turn> turns = new ArrayList<>(List.of(new Dtos.Turn("AI", say, Instant.now().toString())));
        Ctx saved = save(user, c, turns, false, null);
        return view(saved, null);
    }

    /** The candidate speaks; the AI answers, and decides whether the meeting is over. */
    public Dtos.MeetingState reply(AppUser user, Dtos.MeetingReply req) throws Exception {
        Ctx c = load(user, req.sessionKey(), true);
        if (c.turns().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Start the meeting first.");
        }
        if (c.ended()) {
            throw new ApiException(HttpStatus.CONFLICT, "This meeting has already ended.");
        }
        String said = clean(req.text());
        List<Dtos.Turn> turns = new ArrayList<>(c.turns());
        turns.add(new Dtos.Turn("YOU", said, Instant.now().toString()));
        int replies = c.replies() + 1;

        List<Map<String, String>> history = new ArrayList<>();
        for (Dtos.Turn t : turns) {
            history.add(Map.of("role", t.role().equals("AI") ? "assistant" : "user", "content", t.text()));
        }
        boolean last = replies >= MAX_REPLIES;
        String note = last
                ? "(That was the candidate's final reply: the meeting time is up. Close the meeting now in one or "
                        + "two sentences: end=true, and satisfied=true only if your concerns were genuinely addressed.)"
                : replies < MIN_REPLIES
                        ? "(Continue the meeting. Do not end it yet.)"
                        : "(Continue, or close the meeting if your concerns have been convincingly addressed.)";
        history.add(Map.of("role", "user", "content", note));

        JsonNode r = within(45, "The participant took too long to answer. Please press Send again.",
                () -> ai.chat(persona(c), history, model, 0.7));
        String say = clean(r.path("say").asText(""));
        boolean end = last || (replies >= MIN_REPLIES && r.path("end").asBoolean(false));
        Boolean satisfied = end ? r.path("satisfied").asBoolean(false) : null;
        if (say.isBlank()) {
            say = end ? "Thank you, that is all for today." : "Can you say a little more about that?";
        }
        turns.add(new Dtos.Turn("AI", say, Instant.now().toString()));
        Ctx saved = save(user, c, turns, end, satisfied);
        return view(saved, null);
    }

    /**
     * The AI's line number {@code index}, spoken, as MP3. Fetched by the browser after the text has
     * already appeared, so a slow voice service never holds up the conversation.
     */
    public byte[] voice(AppUser user, String key, int index) throws Exception {
        Ctx c = load(user, key, false);
        if (index < 0 || index >= c.turns().size() || !c.turns().get(index).role().equals("AI")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No such line.");
        }
        String id = c.answerId() + ":" + index;
        byte[] cached = voices.get(id);
        if (cached != null) return cached;
        String text = c.turns().get(index).text();
        byte[] mp3 = within(25, "The voice is not available right now; the transcript has the text.",
                () -> ai.speech(text, VOICE, "You are a participant in a work video meeting. Speak naturally and "
                        + "conversationally, at a normal pace, matching the mood of what you are saying."));
        voices.put(id, mp3);
        return mp3;
    }

    /** The candidate's recording, as text they can check and correct before sending. */
    public String transcribe(AppUser user, String key, byte[] audio, String filename) throws Exception {
        Ctx c = load(user, key, true);
        if (c.ended()) {
            throw new ApiException(HttpStatus.CONFLICT, "This meeting has already ended.");
        }
        if (audio == null || audio.length < 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The recording was too short. Please try again.");
        }
        return ai.transcribe(audio, filename == null || filename.isBlank() ? "reply.webm" : filename);
    }

    // ------------------------------------------------------------------ the live call

    /**
     * Opens (or re-opens, after a reload) the live voice meeting. The browser gets a one-time secret
     * and talks to the AI directly, so there is no relay delay: the AI hears the candidate and answers
     * in about a second, like a real call.
     */
    public Dtos.MeetingSession session(AppUser user, String key) throws Exception {
        Ctx c = load(user, key, true);
        if (c.ended()) {
            throw new ApiException(HttpStatus.CONFLICT, "This meeting has already ended.");
        }
        Map<String, Object> session = new java.util.LinkedHashMap<>();
        session.put("type", "realtime");
        session.put("model", liveModel);
        session.put("instructions", liveInstructions(c));
        // MANUAL TURNS: the candidate presses the microphone, speaks, stops, checks what was heard and
        // presses Send. turn_detection is null, so nothing is answered until Send -- noise, pauses and
        // the participant's own voice can never start a turn.
        Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("transcription", Map.of("model", "gpt-4o-mini-transcribe", "language", "en"));
        input.put("noise_reduction", Map.of("type", "near_field"));
        input.put("turn_detection", null);
        session.put("audio", Map.of("input", input, "output", Map.of("voice", "marin")));
        session.put("tools", List.of(Map.of(
                "type", "function", "name", "end_meeting",
                "description", "Call this right after you have said your closing remark, to end the meeting.",
                "parameters", Map.of("type", "object",
                        "properties", Map.of("satisfied", Map.of("type", "boolean",
                                "description", "true only if the candidate convincingly addressed your concerns")),
                        "required", List.of("satisfied")))));
        session.put("tool_choice", "auto");
        JsonNode r = within(20, "The meeting could not connect. Please press Start again.",
                () -> ai.realtimeSecret(session));
        String secret = r.path("value").asText("");
        if (!secret.startsWith("ek_")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The meeting could not connect. Please press Start again.");
        }
        return new Dtos.MeetingSession(secret, liveModel, c.turns(), MIN_REPLIES, MAX_REPLIES);
    }

    /**
     * The conversation as the browser heard it, sent as it grows. It may only GROW: lines already
     * stored cannot be changed or removed, so nothing said can be taken back after the fact.
     */
    public Dtos.MeetingState transcript(AppUser user, String key, List<Dtos.Turn> incoming) {
        Ctx c = load(user, key, false);
        if (c.ended()) {
            return view(c, null);
        }
        List<Dtos.Turn> clean = new ArrayList<>();
        for (Dtos.Turn t : incoming == null ? List.<Dtos.Turn>of() : incoming) {
            String role = "AI".equals(t.role()) ? "AI" : "YOU";
            String text = clean(t.text());
            if (text.isBlank()) continue;
            clean.add(new Dtos.Turn(role, text.length() > 4000 ? text.substring(0, 4000) : text,
                    t.at() == null ? Instant.now().toString() : t.at()));
        }
        if (clean.size() < c.turns().size()) {
            return view(c, null);
        }
        for (int i = 0; i < c.turns().size(); i++) {
            if (!c.turns().get(i).role().equals(clean.get(i).role()) || !c.turns().get(i).text().equals(clean.get(i).text())) {
                throw new ApiException(HttpStatus.CONFLICT, "The meeting record is out of step. Please reload the page.");
            }
        }
        if (clean.size() == c.turns().size()) {
            return view(c, null);
        }
        return view(save(user, c, clean, false, null), null);
    }

    /** The AI closed the meeting (or time ran out). From here the transcript is final. */
    public Dtos.MeetingState end(AppUser user, String key, boolean satisfied) {
        Ctx c = load(user, key, false);
        if (c.ended()) {
            return view(c, null);
        }
        // An AI that says it is satisfied before the candidate has said much has not been convinced.
        boolean ok = satisfied && c.replies() >= 2;
        return view(save(user, c, c.turns(), true, ok), null);
    }

    private String liveInstructions(Ctx c) {
        StringBuilder p = new StringBuilder();
        p.append("""
                You are on a live voice call that is part of a workplace communication assessment at CloudFuze,
                a cloud data migration company. You play the OTHER participant in the meeting: the customer,
                stakeholder, colleague or team member the situation describes. You are never the candidate,
                never a coach and never an assessor. Speak English only.

                """);
        p.append("Situation (it says who the candidate is):\n").append(c.scenario()).append("\n\n");
        p.append("What the candidate has been asked to do: ").append(c.task()).append("\n\n");
        p.append("PRIVATE NOTES. What a strong candidate would cover. Never mention, list or hint that these exist; ")
                .append("use them only to decide what to ask about next:\n");
        for (int i = 0; i < c.points().size(); i++) {
            p.append(i + 1).append(". ").append(c.points().get(i)).append("\n");
        }
        p.append("""

                How to behave on the call:
                - Talk like a real person in a video meeting: natural, one to three short sentences at a time,
                  then stop and let the candidate answer. Match the mood of the situation.
                - You speak first: open the meeting by raising your concern in character.
                - After each answer, react to what the candidate actually said, then ask ONE follow-up question
                  or push back, on the most important point from your notes they have not covered well yet.
                - Be hard to satisfy. Vague answers ("we will do our best", "we will look into it", "it will be
                  fine") are not enough: ask for specifics such as what exactly, by when, who owns it, and how
                  you will be kept informed. Do not help the candidate or suggest answers.
                - If the candidate blames you or your team, or is rude, dismissive or defensive, say plainly that
                  you are not comfortable with that, become more concerned, and do not become satisfied.
                - Stay consistent with the situation. Never contradict its facts.
                """);
        p.append("- Keep the meeting going until the candidate has answered at least ").append(MIN_REPLIES)
                .append(" times. You may close earlier only if the candidate clearly ends the meeting themselves.\n");
        p.append("""
                - When your concerns have been convincingly addressed, or when you are told the time is up: give
                  a short closing remark that is NOT a question (thank them, and say what happens next), then
                  call end_meeting. Set satisfied=true only if the candidate really earned it.
                """);
        if (!c.turns().isEmpty()) {
            p.append("\nThe call was interrupted and has just reconnected. The conversation so far:\n");
            for (Dtos.Turn t : c.turns()) {
                p.append(t.role().equals("AI") ? "YOU (participant): " : "CANDIDATE: ").append(t.text()).append("\n");
            }
            p.append("Continue naturally from where it stopped. Do not repeat your opening.\n");
        }
        return p.toString();
    }

    // ------------------------------------------------------------------ the role

    private String persona(Ctx c) {
        StringBuilder p = new StringBuilder();
        p.append("""
                You are taking part in a realistic workplace meeting that forms part of a communication
                assessment at CloudFuze, a cloud data migration company. You play the OTHER participant in the
                meeting: the customer, stakeholder, colleague or team member the situation describes. You are
                never the candidate and never an assessor.

                """);
        p.append("Situation (it says who the candidate is):\n").append(c.scenario()).append("\n\n");
        p.append("What the candidate has been asked to do: ").append(c.task()).append("\n\n");
        p.append("PRIVATE NOTES. What a strong candidate would cover. Never mention, list or hint that these exist; ")
                .append("use them only to decide what to ask about next:\n");
        for (int i = 0; i < c.points().size(); i++) {
            p.append(i + 1).append(". ").append(c.points().get(i)).append("\n");
        }
        p.append("""

                How to behave:
                - Speak like a real person in a video meeting: one to three short sentences, spoken style,
                  no lists, no bullet points, no markdown, no stage directions.
                - Open the meeting by raising your concern or question in character, consistent with the
                  facts and the mood of the situation.
                - In each turn, react to what the candidate actually said, then ask ONE follow-up question or
                  push back, on the most important thing from your private notes they have not yet covered well.
                - Do not help the candidate or suggest answers. Do not accept vague promises: ask for specifics
                  such as what exactly, by when, and who.
                - If the candidate blames you or your team, or is rude, dismissive or defensive, react as a real
                  person would and say so plainly (for example that you are not comfortable with that answer, or
                  that you expected more ownership). Become more concerned or upset, and do not become satisfied.
                - Stay consistent with the situation; you may add small realistic details but never contradict it.
                - Notes in brackets from the system are instructions to you, not things the candidate said.
                - Close the meeting politely (end=true) when your concerns have been convincingly addressed.

                Reply as JSON only: {"say":"<your next line>","end":<true|false>,"satisfied":<true|false>}""");
        return p.toString();
    }

    // ------------------------------------------------------------------ storage

    private Ctx load(AppUser user, String key, boolean mustBeOpen) {
        return tx.execute(s -> {
            Attempt a = attempts.lockByUserId(user.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You have not started the test."));
            if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
                throw new ApiException(HttpStatus.CONFLICT, "Your test has already ended.");
            }
            if (key == null || !key.equals(a.getSessionKey())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Your test was opened in another window. Continue there, or reopen it here.");
            }
            List<Part> order = Arrays.stream(a.getPartOrder().split(",")).map(Part::valueOf).toList();
            if (order.get(a.getPartIndex()) != Part.MEETING) {
                throw new ApiException(HttpStatus.CONFLICT, "The meeting part is not open.");
            }
            AttemptPart ap = parts.findByAttemptIdAndPart(a.getId(), Part.MEETING).orElseThrow();
            if (mustBeOpen && Instant.now().isAfter(ap.getDeadline().plus(grace))) {
                throw new ApiException(HttpStatus.GONE, "Time is up for this part.");
            }
            Answer ans = answers.findByAttemptIdOrderByIdAsc(a.getId()).stream()
                    .filter(x -> x.getPart() == Part.MEETING).findFirst().orElseThrow();
            Question q = ans.getQuestion();
            return new Ctx(ans.getId(), q.getTitle(), q.getScenario(), q.getTask(), points(q),
                    turnsOf(ans.getTranscriptJson()), ans.isMeetingEnded(), ans.getMeetingSatisfied());
        });
    }

    private Ctx save(AppUser user, Ctx before, List<Dtos.Turn> turns, boolean ended, Boolean satisfied) {
        return tx.execute(s -> {
            attempts.lockByUserId(user.getId());
            Answer ans = answers.findById(before.answerId()).orElseThrow();
            // Two replies sent together: only the first may land, or one would silently overwrite the other.
            if (turnsOf(ans.getTranscriptJson()).size() != before.turns().size()) {
                throw new ApiException(HttpStatus.CONFLICT, "Your previous reply is still being answered.");
            }
            try {
                ans.setTranscriptJson(json.writeValueAsString(turns));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            String mine = String.join("\n\n", turns.stream().filter(t -> t.role().equals("YOU")).map(Dtos.Turn::text).toList());
            ans.setContent(mine);
            ans.setWordCount(TestService.words(mine));
            ans.setSavedAt(Instant.now());
            ans.setMeetingEnded(ended);
            ans.setMeetingSatisfied(satisfied);
            return new Ctx(before.answerId(), before.title(), before.scenario(), before.task(), before.points(),
                    turns, ended, satisfied);
        });
    }

    private Dtos.MeetingState view(Ctx c, String audio) {
        String speaker = c.ended() ? null : c.turns().isEmpty() ? null : "YOU";
        return new Dtos.MeetingState(c.turns(), !c.turns().isEmpty(), c.ended(), c.satisfied(), c.replies(),
                MAX_REPLIES, speaker, audio);
    }

    private List<String> points(Question q) {
        try {
            List<Dtos.Metric> m = json.readValue(q.getMetricsJson(), new TypeReference<>() { });
            return m.stream().map(Dtos.Metric::name).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Dtos.Turn> turnsOf(String raw) {
        try {
            return raw == null || raw.isBlank() ? List.of() : json.readValue(raw, new TypeReference<>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[*_#`]+", "").trim();
    }
}
