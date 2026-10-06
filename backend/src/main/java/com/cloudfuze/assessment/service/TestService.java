package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.AttemptStatus;
import com.cloudfuze.assessment.domain.Part;
import com.cloudfuze.assessment.domain.Role;
import com.cloudfuze.assessment.domain.ScoreStatus;
import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.entity.Answer;
import com.cloudfuze.assessment.entity.AppUser;
import com.cloudfuze.assessment.entity.Attempt;
import com.cloudfuze.assessment.entity.AttemptPart;
import com.cloudfuze.assessment.entity.ProctorEvent;
import com.cloudfuze.assessment.entity.Question;
import com.cloudfuze.assessment.exception.ApiException;
import com.cloudfuze.assessment.repository.AnswerRepository;
import com.cloudfuze.assessment.repository.AttemptPartRepository;
import com.cloudfuze.assessment.repository.AttemptRepository;
import com.cloudfuze.assessment.repository.ProctorEventRepository;
import com.cloudfuze.assessment.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The one sitting: start, write, move on, get caught, finish.
 *
 * <p>THE SERVER OWNS THE CLOCK. Each part's deadline is fixed when the part begins, and nothing
 * the browser sends can move it. A part whose deadline has passed is closed the next time anyone
 * looks at the attempt -- the candidate's own screen, or the sweeper below -- so closing the
 * browser does not stop the hour: the parts still end on time and the test is submitted.
 *
 * <p>ONE BROWSER AT A TIME. The session key is kept in the tab's sessionStorage, so a reload
 * keeps it. Opening the test anywhere else issues a new key, which ends the old tab's session
 * and counts as a violation.
 */
@Service
public class TestService {

    private static final Logger log = LoggerFactory.getLogger(TestService.class);

    /** Events that count towards ending the test. Everything else is recorded, not counted. */
    public static final Set<String> COUNTED = Set.of("TAB_HIDDEN", "WINDOW_BLUR", "FULLSCREEN_EXIT", "NEW_WINDOW");

    private final AttemptRepository attempts;
    private final AttemptPartRepository parts;
    private final AnswerRepository answers;
    private final QuestionRepository questions;
    private final ProctorEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final MarksPolicy marks;
    private final Duration partLength;
    private final Duration grace;
    private final int maxViolations;

    public TestService(AttemptRepository attempts, AttemptPartRepository parts, AnswerRepository answers,
                       QuestionRepository questions, ProctorEventRepository events,
                       ApplicationEventPublisher publisher, MarksPolicy marks,
                       @Value("${app.test.part-minutes:20}") int partMinutes,
                       @Value("${app.test.grace-seconds:20}") int graceSeconds,
                       @Value("${app.test.max-violations:3}") int maxViolations) {
        this.attempts = attempts;
        this.parts = parts;
        this.answers = answers;
        this.questions = questions;
        this.events = events;
        this.publisher = publisher;
        this.marks = marks;
        this.partLength = Duration.ofMinutes(partMinutes);
        this.grace = Duration.ofSeconds(graceSeconds);
        this.maxViolations = maxViolations;
    }

    // ------------------------------------------------------------------ before the test

    @Transactional(readOnly = true)
    public Dtos.Overview overview(AppUser user) {
        String status = attempts.findByUserId(user.getId())
                .map(a -> a.getStatus() == AttemptStatus.IN_PROGRESS ? "IN_PROGRESS" : "DONE").orElse("NOT_STARTED");
        List<Dtos.PartInfo> list = new ArrayList<>();
        for (Part p : Part.values()) {
            // Each candidate is given ONE question per part, however many the bank holds.
            if (!questions.findByPartAndActiveTrueOrderByOrdinalAscIdAsc(p).isEmpty()) {
                list.add(new Dtos.PartInfo(p.name(), p.label(), (int) partLength.toMinutes(), 1, "UPCOMING"));
            }
        }
        return new Dtos.Overview(status, (int) partLength.toMinutes() * list.size(), maxViolations, list);
    }

    @Transactional
    public Dtos.TestState start(AppUser user, String userAgent, String ip) {
        if (user.getRole() != Role.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admins cannot sit the test.");
        }
        if (!user.isProfileComplete()) {
            throw new ApiException(HttpStatus.CONFLICT, "Please complete your details before starting.");
        }
        if (attempts.findByUserId(user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already started this test. Only one attempt is allowed.");
        }
        Map<Part, List<Question>> byPart = new EnumMap<>(Part.class);
        for (Part p : Part.values()) {
            Question q = pickFor(p);
            if (q != null) byPart.put(p, List.of(q));
        }
        if (byPart.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "The test is not ready yet. Please try again later.");
        }

        Instant now = Instant.now();
        Attempt a = new Attempt();
        a.setUser(user);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setPartOrder(String.join(",", byPart.keySet().stream().map(Enum::name).toList()));
        a.setPartIndex(0);
        a.setSessionKey(newKey());
        a.setStartedAt(now);
        a.setUserAgent(trim(userAgent, 400));
        a.setIpAddress(trim(ip, 64));
        attempts.save(a);

        // The questions are fixed now: editing the bank later never changes a test already begun.
        Map<Part, Double> maxByPart = marks.maxMarks(byPart.keySet());
        byPart.forEach((p, qs) -> {
            double each = maxByPart.get(p) / qs.size();
            for (Question q : qs) {
                Answer ans = new Answer();
                ans.setAttempt(a);
                ans.setQuestion(q);
                ans.setPart(p);
                ans.setContent("");
                ans.setMaxMarks(each);
                ans.setScoreStatus(ScoreStatus.PENDING);
                answers.save(ans);
            }
        });
        beginPart(a, partAt(a, 0), now);
        log.info("Attempt {} started by {}", a.getId(), user.getEmail());
        return state(a);
    }

    /**
     * One question for this part, never one somebody else already had.
     *
     * <p>Chosen at random among the questions given out the FEWEST times. While the bank has unused
     * questions that means an unused one, so no two candidates see the same scenario; only once all
     * fifty are used does a question come round again, and then evenly. The part's rows are locked
     * first, so two candidates starting in the same second cannot both be handed the same one.
     */
    private Question pickFor(Part p) {
        List<Question> pool = questions.lockActiveByPart(p);
        if (pool.isEmpty()) {
            return null;
        }
        Map<Long, Long> used = new java.util.HashMap<>();
        for (Object[] r : answers.usage()) used.put((Long) r[0], (Long) r[1]);
        long least = pool.stream().mapToLong(q -> used.getOrDefault(q.getId(), 0L)).min().orElse(0);
        List<Question> fresh = pool.stream().filter(q -> used.getOrDefault(q.getId(), 0L) == least).toList();
        return fresh.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(fresh.size()));
    }

    /** A tab with no session key: the test was opened somewhere new. Allowed, but it counts. */
    @Transactional
    public Dtos.TestState resume(AppUser user) {
        Attempt a = locked(user);
        requireInProgress(a);
        advanceIfExpired(a);
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            return state(a);
        }
        a.setSessionKey(newKey());
        record(a, "NEW_WINDOW", "The test was opened in a new window or browser", true);
        return state(a);
    }

    // ------------------------------------------------------------------ during the test

    @Transactional
    public Dtos.TestState current(AppUser user, String key) {
        Attempt a = locked(user);
        checkKey(a, key);
        advanceIfExpired(a);
        return state(a);
    }

    @Transactional
    public void save(AppUser user, Dtos.SaveRequest r) {
        Attempt a = locked(user);
        checkKey(a, r.sessionKey());
        requireInProgress(a);
        Part part = partAt(a, a.getPartIndex());
        AttemptPart ap = parts.findByAttemptIdAndPart(a.getId(), part).orElseThrow();
        if (Instant.now().isAfter(ap.getDeadline().plus(grace))) {
            advanceIfExpired(a);
            throw new ApiException(HttpStatus.GONE, "Time is up for this part.");
        }
        Answer ans = answers.findByAttemptIdAndQuestionId(a.getId(), r.questionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Question not found."));
        if (ans.getPart() != part) {
            throw new ApiException(HttpStatus.CONFLICT, "This part is already closed.");
        }
        String content = r.content() == null ? "" : r.content();
        ans.setContent(content);
        ans.setSubject(r.subject() == null ? null : r.subject().trim());
        ans.setWordCount(words(content));
        ans.setKeystrokes(Math.max(ans.getKeystrokes(), r.keystrokes()));
        ans.setPasteBlocked(Math.max(ans.getPasteBlocked(), r.pasteBlocked()));
        ans.setSavedAt(Instant.now());
    }

    /** The candidate has finished this part early and moves on. There is no going back. */
    @Transactional
    public Dtos.TestState next(AppUser user, String key) {
        Attempt a = locked(user);
        checkKey(a, key);
        requireInProgress(a);
        if (!advanceIfExpired(a)) {
            finishPart(a, Instant.now());
        }
        return state(a);
    }

    @Transactional
    public Dtos.TestState submit(AppUser user, String key) {
        Attempt a = locked(user);
        checkKey(a, key);
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            Instant now = Instant.now();
            parts.findByAttemptIdAndPart(a.getId(), partAt(a, a.getPartIndex()))
                    .ifPresent(ap -> ap.setEndedAt(now));
            finish(a, AttemptStatus.SUBMITTED, "Submitted by the candidate", now);
        }
        return state(a);
    }

    @Transactional
    public Dtos.ProctorResult proctor(AppUser user, Dtos.ProctorRequest r) {
        Attempt a = locked(user);
        checkKey(a, r.sessionKey());
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            return new Dtos.ProctorResult(a.getViolations(), maxViolations, false, a.getStatus() == AttemptStatus.TERMINATED);
        }
        String type = r.type().trim().toUpperCase();
        // Focus lost to the test's own microphone prompt is excused -- but only a few times, so the
        // excuse cannot be used to leave the page freely.
        if (type.equals("PERMISSION_PROMPT") && events.findByAttemptIdOrderByAtAsc(a.getId()).stream()
                .filter(e -> e.getType().equals("PERMISSION_PROMPT")).count() >= 3) {
            type = "WINDOW_BLUR";
        }
        boolean counted = COUNTED.contains(type) && !recentlyCounted(a);
        record(a, type, r.detail(), counted);
        return new Dtos.ProctorResult(a.getViolations(), maxViolations, counted,
                a.getStatus() == AttemptStatus.TERMINATED);
    }

    /**
     * Leaving the tab fires "hidden" and "blur" together, a second apart. One act, one strike:
     * a counted event within three seconds of the last one is recorded but not counted again.
     */
    private boolean recentlyCounted(Attempt a) {
        Instant cutoff = Instant.now().minusSeconds(3);
        return events.findByAttemptIdOrderByAtAsc(a.getId()).stream()
                .anyMatch(e -> e.isCounted() && e.getAt().isAfter(cutoff));
    }

    private void record(Attempt a, String type, String detail, boolean counted) {
        ProctorEvent e = new ProctorEvent();
        e.setAttempt(a);
        e.setType(type);
        e.setDetail(trim(detail, 400));
        e.setPart(a.getStatus() == AttemptStatus.IN_PROGRESS ? partAt(a, a.getPartIndex()) : null);
        e.setAt(Instant.now());
        e.setCounted(counted);
        if (counted) {
            a.setViolations(a.getViolations() + 1);
            e.setViolationNumber(a.getViolations());
        }
        events.save(e);
        if (counted && a.getViolations() >= maxViolations && a.getStatus() == AttemptStatus.IN_PROGRESS) {
            Instant now = Instant.now();
            parts.findByAttemptIdAndPart(a.getId(), partAt(a, a.getPartIndex())).ifPresent(ap -> ap.setEndedAt(now));
            finish(a, AttemptStatus.TERMINATED, "Ended after " + maxViolations + " violations", now);
        }
    }

    // ------------------------------------------------------------------ the clock

    /** Closes every part whose time has run out. Returns true if anything moved. */
    boolean advanceIfExpired(Attempt a) {
        boolean moved = false;
        while (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            AttemptPart ap = parts.findByAttemptIdAndPart(a.getId(), partAt(a, a.getPartIndex())).orElseThrow();
            if (!Instant.now().isAfter(ap.getDeadline().plus(grace))) {
                break;
            }
            // The next part starts when this one was due to end, so the hour stays continuous
            // even for a candidate who walked away.
            finishPart(a, ap.getDeadline());
            moved = true;
        }
        return moved;
    }

    private void finishPart(Attempt a, Instant at) {
        AttemptPart ap = parts.findByAttemptIdAndPart(a.getId(), partAt(a, a.getPartIndex())).orElseThrow();
        ap.setEndedAt(at);
        int next = a.getPartIndex() + 1;
        if (next >= partCount(a)) {
            boolean timedOut = !at.isBefore(ap.getDeadline());
            finish(a, AttemptStatus.SUBMITTED, timedOut ? "Time over" : "Submitted by the candidate", at);
            return;
        }
        a.setPartIndex(next);
        beginPart(a, partAt(a, next), at);
    }

    private void beginPart(Attempt a, Part p, Instant at) {
        AttemptPart ap = new AttemptPart();
        ap.setAttempt(a);
        ap.setPart(p);
        ap.setStartedAt(at);
        ap.setDeadline(at.plus(partLength));
        parts.save(ap);
    }

    private void finish(Attempt a, AttemptStatus status, String reason, Instant at) {
        a.setStatus(status);
        a.setEndReason(reason);
        a.setSubmittedAt(at);
        a.setSessionKey(null);
        publisher.publishEvent(new AttemptFinished(a.getId()));
        log.info("Attempt {} ended: {}", a.getId(), reason);
    }

    /** Every 30 seconds: close the parts of anyone who stopped looking at their screen. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void sweep() {
        for (Attempt a : attempts.findByStatus(AttemptStatus.IN_PROGRESS)) {
            attempts.lockById(a.getId()).ifPresent(this::advanceIfExpired);
        }
    }

    /** The candidate's own marks: the total and each part, nothing more. */
    @Transactional(readOnly = true)
    public Dtos.MyResult result(AppUser user) {
        Attempt a = attempts.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You have not taken the test."));
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Your test is still in progress.");
        }
        List<Dtos.PartScore> list = new ArrayList<>();
        boolean ready = true;
        for (Part p : order(a)) {
            double marks = 0;
            double max = 0;
            for (Answer x : answers.findByAttemptIdOrderByIdAsc(a.getId())) {
                if (x.getPart() != p) continue;
                max += x.getMaxMarks() == null ? 0 : x.getMaxMarks();
                if (x.getMarks() == null) ready = false;
                else marks += x.getMarks();
            }
            list.add(new Dtos.PartScore(p.name(), p.label(), (double) Math.round(marks), Math.round(max)));
        }
        ready = ready && a.isScoringDone();
        double max = list.stream().mapToDouble(Dtos.PartScore::maxMarks).sum();
        Double got = ready ? list.stream().mapToDouble(x -> x.marks() == null ? 0 : x.marks()).sum() : null;
        return new Dtos.MyResult(a.getStatus().name(), ready, ready ? a.getTotalMarks() : null, got, max,
                ready ? list : list.stream().map(x -> new Dtos.PartScore(x.part(), x.label(), null, x.maxMarks())).toList());
    }

    // ------------------------------------------------------------------ the screen

    private Dtos.TestState state(Attempt a) {
        List<Part> order = order(a);
        List<Dtos.PartInfo> infos = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Part p = order.get(i);
            String s = a.getStatus() != AttemptStatus.IN_PROGRESS || i < a.getPartIndex() ? "DONE"
                    : i == a.getPartIndex() ? "CURRENT" : "UPCOMING";
            infos.add(new Dtos.PartInfo(p.name(), p.label(), (int) partLength.toMinutes(), 0, s));
        }
        long now = System.currentTimeMillis();
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            return new Dtos.TestState("DONE", null, a.getViolations(), maxViolations, infos, null, null, 0,
                    order.size(), 0, now, List.of());
        }
        Part p = order.get(a.getPartIndex());
        AttemptPart ap = parts.findByAttemptIdAndPart(a.getId(), p).orElseThrow();
        List<Dtos.QuestionView> qs = answers.findByAttemptIdOrderByIdAsc(a.getId()).stream()
                .filter(x -> x.getPart() == p)
                .map(x -> new Dtos.QuestionView(x.getQuestion().getId(), x.getQuestion().getTitle(),
                        x.getQuestion().getScenario(), x.getQuestion().getTask(), x.getSubject(), x.getContent()))
                .toList();
        return new Dtos.TestState("IN_PROGRESS", a.getSessionKey(), a.getViolations(), maxViolations, infos,
                p.name(), p.label(), a.getPartIndex() + 1, order.size(), ap.getDeadline().toEpochMilli(), now, qs);
    }

    // ------------------------------------------------------------------ helpers

    private Attempt locked(AppUser user) {
        return attempts.lockByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You have not started the test."));
    }

    private void checkKey(Attempt a, String key) {
        if (a.getStatus() == AttemptStatus.IN_PROGRESS && (key == null || !key.equals(a.getSessionKey()))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Your test was opened in another window. Continue there, or reopen it here.");
        }
    }

    private void requireInProgress(Attempt a) {
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Your test has already ended.");
        }
    }

    private static List<Part> order(Attempt a) {
        return Arrays.stream(a.getPartOrder().split(",")).map(Part::valueOf).toList();
    }

    private static Part partAt(Attempt a, int i) {
        return order(a).get(i);
    }

    private static int partCount(Attempt a) {
        return order(a).size();
    }

    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static int words(String s) {
        return s == null || s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
