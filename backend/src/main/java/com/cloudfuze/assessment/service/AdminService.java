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
import com.cloudfuze.assessment.repository.AppUserRepository;
import com.cloudfuze.assessment.repository.AttemptPartRepository;
import com.cloudfuze.assessment.repository.AttemptRepository;
import com.cloudfuze.assessment.repository.ProctorEventRepository;
import com.cloudfuze.assessment.repository.QuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The admin report, the question bank, and re-marking. */
@Service
public class AdminService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final AttemptRepository attempts;
    private final AttemptPartRepository parts;
    private final AnswerRepository answers;
    private final ProctorEventRepository events;
    private final QuestionRepository questions;
    private final AppUserRepository users;
    private final ScoringService scoring;
    private final MeetingService meetings;
    private final com.cloudfuze.assessment.repository.AttemptResetRepository resets;

    public AdminService(MeetingService meetings, com.cloudfuze.assessment.repository.AttemptResetRepository resets, AttemptRepository attempts, AttemptPartRepository parts, AnswerRepository answers,
                        ProctorEventRepository events, QuestionRepository questions, AppUserRepository users,
                        ScoringService scoring) {
        this.attempts = attempts;
        this.parts = parts;
        this.answers = answers;
        this.events = events;
        this.questions = questions;
        this.users = users;
        this.scoring = scoring;
        this.meetings = meetings;
        this.resets = resets;
    }

    /**
     * Clears a candidate's test so they can sit it again.
     *
     * <p>The attempt, its answers, timings and activity log are deleted -- the one-attempt rule is
     * enforced by there being one attempt row per person -- and a row in attempt_reset keeps what is
     * gone: the old status and overall, who cleared it, when, and why. The candidate's details stay.
     */
    @Transactional
    public void reset(Long attemptId, String adminEmail, String reason) {
        Attempt a = attempts.lockById(attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This test has already been cleared."));
        AppUser u = a.getUser();
        com.cloudfuze.assessment.entity.AttemptReset r = new com.cloudfuze.assessment.entity.AttemptReset();
        r.setCandidateEmail(u.getEmail());
        r.setCandidateName(u.getName());
        r.setPreviousStatus(a.getStatus().name());
        r.setPreviousOverall(a.getTotalMarks());
        r.setPreviousEndReason(a.getEndReason());
        r.setPreviousStartedAt(a.getStartedAt());
        r.setClearedBy(adminEmail);
        r.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        r.setClearedAt(Instant.now());
        resets.save(r);

        events.deleteAll(events.findByAttemptIdOrderByAtAsc(attemptId));
        answers.deleteAll(answers.findByAttemptIdOrderByIdAsc(attemptId));
        parts.deleteAll(parts.findByAttemptIdOrderByIdAsc(attemptId));
        attempts.delete(a);
    }

    private List<Dtos.ResetRow> resetsOf(String email) {
        return resets.findByCandidateEmailIgnoreCaseOrderByClearedAtDesc(email).stream()
                .map(r -> new Dtos.ResetRow(stamp(r.getClearedAt()), r.getClearedBy(), r.getPreviousStatus(),
                        r.getPreviousOverall(), r.getPreviousEndReason(), r.getReason()))
                .toList();
    }

    // ------------------------------------------------------------------ report

    /**
     * Everyone who has signed in as a candidate, not only those who started: a person who filled in
     * their details and never began is exactly who an admin needs to chase. Those rows have no
     * attempt id, status NOT_STARTED (or DETAILS_PENDING before the details form is done).
     */
    @Transactional(readOnly = true)
    public List<Dtos.AttemptRow> rows(String team) {
        List<Dtos.AttemptRow> out = new ArrayList<>();
        java.util.Set<Long> started = new java.util.HashSet<>();
        for (Attempt a : attempts.findAllWithUser()) {
            started.add(a.getUser().getId());
            if (blank(team) || team.equalsIgnoreCase(a.getUser().getTeam())) out.add(row(a));
        }
        for (AppUser u : users.findByRoleOrderByCreatedAtDesc(com.cloudfuze.assessment.domain.Role.CANDIDATE)) {
            if (started.contains(u.getId()) || !(blank(team) || team.equalsIgnoreCase(u.getTeam()))) continue;
            out.add(new Dtos.AttemptRow(null, u.getName() != null ? u.getName() : u.getMicrosoftName(), u.getEmail(),
                    u.getEmployeeId(), u.getTeam(), u.getJobRole(),
                    u.isProfileComplete() ? "NOT_STARTED" : "DETAILS_PENDING", null, null, null, null, 0, null,
                    "WAITING", List.of(), null, null));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Dtos.Summary summary(String team) {
        List<Dtos.AttemptRow> rows = rows(team);
        Map<String, Integer> byTeam = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Dtos.AttemptRow r : rows(null)) {
            byTeam.merge(r.team() == null ? "No team" : r.team(), 1, Integer::sum);
        }
        Double avg = rows.stream().filter(r -> r.totalMarks() != null).mapToDouble(Dtos.AttemptRow::totalMarks)
                .average().stream().map(d -> (double) Math.round(d)).boxed().findFirst().orElse(null);
        return new Dtos.Summary(rows.size(),
                (int) rows.stream().filter(r -> r.attemptId() == null).count(),
                (int) rows.stream().filter(r -> r.status().equals("SUBMITTED")).count(),
                (int) rows.stream().filter(r -> r.status().equals("IN_PROGRESS")).count(),
                (int) rows.stream().filter(r -> r.status().equals("TERMINATED")).count(),
                avg, users.findDistinctTeams(), byTeam);
    }

    @Transactional(readOnly = true)
    public Dtos.AttemptDetail detail(Long id) {
        Attempt a = attempts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attempt not found."));
        List<Dtos.PartTiming> timings = parts.findByAttemptIdOrderByIdAsc(id).stream().map(p -> new Dtos.PartTiming(
                p.getPart().name(), p.getPart().label(), stamp(p.getStartedAt()), stamp(p.getEndedAt()),
                p.getEndedAt() == null ? null : (int) Duration.between(p.getStartedAt(), p.getEndedAt()).toSeconds()))
                .toList();
        List<Dtos.AnswerDetail> ans = answers.findByAttemptIdOrderByIdAsc(id).stream().map(x -> {
            Question q = x.getQuestion();
            return new Dtos.AnswerDetail(q.getId(), q.getCode(), x.getPart().name(), q.getTitle(), q.getScenario(), q.getTask(),
                    x.getSubject(), x.getContent(), x.getWordCount(), x.getKeystrokes(), x.getPasteBlocked(),
                    x.getScoreStatus() == null ? null : x.getScoreStatus().name(), x.getMarks(), round1(x.getMaxMarks()),
                    x.getPercent(), scoring.results(x), x.getAiSummary(), x.getScoreError(),
                    meetings.turnsOf(x.getTranscriptJson()), x.getMeetingSatisfied());
        }).toList();
        List<Dtos.EventRow> ev = events.findByAttemptIdOrderByAtAsc(id).stream().map(e -> new Dtos.EventRow(
                e.getType(), e.getDetail(), e.getPart() == null ? null : e.getPart().name(), e.isCounted(),
                e.getViolationNumber(), stamp(e.getAt()))).toList();
        return new Dtos.AttemptDetail(row(a), timings, ans, ev, a.getUserAgent(), a.getIpAddress(),
                resetsOf(a.getUser().getEmail()));
    }

    private Dtos.AttemptRow row(Attempt a) {
        AppUser u = a.getUser();
        Map<Part, double[]> byPart = new LinkedHashMap<>();
        boolean anyMissing = false;
        for (Answer x : answers.findByAttemptIdOrderByIdAsc(a.getId())) {
            double[] v = byPart.computeIfAbsent(x.getPart(), k -> new double[]{0, 0, 0});
            v[1] += x.getMaxMarks() == null ? 0 : x.getMaxMarks();
            if (x.getMarks() == null) {
                v[2] = 1;
                anyMissing = true;
            } else {
                v[0] += x.getMarks();
            }
        }
        List<Dtos.PartScore> ps = new ArrayList<>();
        byPart.forEach((p, v) -> ps.add(new Dtos.PartScore(p.name(), p.label(), v[2] == 1 ? null : round1(v[0]), round1(v[1]))));
        Instant end = a.getSubmittedAt();
        String scoringState = a.getStatus() == AttemptStatus.IN_PROGRESS ? "WAITING"
                : a.isScoringDone() ? "DONE" : anyMissing ? "PENDING" : "DONE";
        return new Dtos.AttemptRow(a.getId(), u.getName(), u.getEmail(), u.getEmployeeId(), u.getTeam(),
                u.getJobRole(), a.getStatus().name(), a.getEndReason(), stamp(a.getStartedAt()), stamp(end),
                end == null ? null : (int) Duration.between(a.getStartedAt(), end).toSeconds(), a.getViolations(),
                a.getTotalMarks(), scoringState, ps,
                ps.stream().anyMatch(x -> x.marks() == null) ? null : ps.stream().mapToDouble(Dtos.PartScore::marks).sum(),
                ps.stream().mapToDouble(Dtos.PartScore::maxMarks).sum());
    }

    public void rescore(Long attemptId, boolean all) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attempt not found."));
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "This test is still in progress.");
        }
        scoring.scoreAttempt(attemptId, all);
    }

    @Transactional(readOnly = true)
    public String csv(String team) {
        StringBuilder b = new StringBuilder(
                "Name,Employee ID,Email,Team,Role,Status,End reason,Started,Submitted,Minutes,Violations,"
                        + "Teams /20,Email /35,Meeting /45,Overall /100\n");
        for (Dtos.AttemptRow r : rows(team)) {
            Map<String, Dtos.PartScore> p = new LinkedHashMap<>();
            r.parts().forEach(x -> p.put(x.part(), x));
            b.append(String.join(",", c(r.name()), c(r.employeeId()), c(r.email()), c(r.team()), c(r.jobRole()),
                    c(r.status()), c(r.endReason()), c(r.startedAt()), c(r.submittedAt()),
                    r.durationSeconds() == null ? "" : String.valueOf(Math.round(r.durationSeconds() / 60.0)),
                    String.valueOf(r.violations()), mark(p.get("TEAMS")), mark(p.get("EMAIL")), mark(p.get("MEETING")),
                    r.totalMarks() == null ? "" : String.valueOf(Math.round(r.totalMarks())))).append("\n");
        }
        return b.toString();
    }

    private static String mark(Dtos.PartScore p) {
        return p == null || p.marks() == null ? "" : String.valueOf(Math.round(p.marks()));
    }

    /** CSV cell: quoted, and never starting with a formula character. */
    private static String c(String v) {
        if (v == null) return "";
        String s = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------ question bank

    @Transactional(readOnly = true)
    public List<Dtos.QuestionDto> questions() {
        Map<Long, Long> used = new java.util.HashMap<>();
        for (Object[] r : answers.usage()) used.put((Long) r[0], (Long) r[1]);
        return questions.findAllByOrderByPartAscOrdinalAscIdAsc().stream()
                .map(q -> dto(q, used.getOrDefault(q.getId(), 0L))).toList();
    }

    @Transactional
    public Dtos.QuestionDto saveQuestion(Long id, Dtos.QuestionRequest r) {
        Part part;
        try {
            part = Part.valueOf(r.part().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Part must be TEAMS, EMAIL or MEETING.");
        }
        if (r.metrics() != null) {
            for (Dtos.Metric m : r.metrics()) {
                if (blank(m.name()) || m.weight() <= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Every metric needs a name and a weight above 0.");
                }
            }
        }
        Question q = id == null ? new Question()
                : questions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Question not found."));
        if (id == null) q.setCreatedAt(Instant.now());
        q.setPart(part);
        q.setOrdinal(r.ordinal());
        q.setTitle(r.title().trim());
        q.setScenario(r.scenario() == null ? null : r.scenario().trim());
        q.setTask(r.task().trim());
        q.setMetricsJson(scoring.metricsJson(r.metrics()));
        q.setActive(r.active());
        if (r.code() != null && !r.code().isBlank()) q.setCode(r.code().trim().toUpperCase());
        if (r.category() != null) q.setCategory(r.category().trim());
        q.setUpdatedAt(Instant.now());
        Question saved = questions.save(q);
        return dto(saved, answers.existsByQuestionId(saved.getId()) ? 1 : 0);
    }

    /** Removing a question that has been answered would break the report, so it is switched off instead. */
    @Transactional
    public void deleteQuestion(Long id) {
        Question q = questions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Question not found."));
        if (answers.existsByQuestionId(id)) {
            q.setActive(false);
        } else {
            questions.delete(q);
        }
    }

    private Dtos.QuestionDto dto(Question q, long used) {
        return new Dtos.QuestionDto(q.getId(), q.getCode(), q.getCategory(), q.getPart().name(), q.getOrdinal(),
                q.getTitle(), q.getScenario(), q.getTask(), scoring.metricsOf(q), q.isActive(), used);
    }

    // ------------------------------------------------------------------ helpers

    private static String stamp(Instant i) {
        return i == null ? null : STAMP.format(i);
    }

    private static Double round1(Double v) {
        return v == null ? null : Math.round(v * 10) / 10.0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("all");
    }
}
