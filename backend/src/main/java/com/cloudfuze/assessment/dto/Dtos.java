package com.cloudfuze.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Every request and response shape the API uses, grouped by area. */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ auth and profile

    public record LoginRequest(@NotBlank String idToken) {
    }

    /** NOT_STARTED, IN_PROGRESS or DONE: all a candidate is ever told about their test. */
    public record Me(Long id, String email, String name, String employeeId, String team, String jobRole,
                     String role, boolean profileComplete, String testStatus) {
    }

    public record LoginResponse(String token, Me me) {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 64) String employeeId,
            @NotBlank @Size(max = 120) String team,
            @NotBlank @Size(max = 120) String jobRole) {
    }

    // ------------------------------------------------------------------ the test

    public record PartInfo(String part, String label, int minutes, int questions, String state) {
    }

    /** What the instructions page shows before Start. */
    public record Overview(String status, int totalMinutes, int maxViolations, List<PartInfo> parts) {
    }

    public record QuestionView(Long id, String title, String scenario, String task, String subject, String content) {
    }

    /**
     * Everything the exam screen needs. {@code deadline} and {@code serverNow} are epoch millis, so
     * the browser counts down against the server's clock, not its own.
     */
    public record TestState(String status, String sessionKey, int violations, int maxViolations,
                            List<PartInfo> parts, String part, String partLabel, int partNumber, int partCount,
                            long deadline, long serverNow, List<QuestionView> questions) {
    }

    public record SaveRequest(@NotBlank String sessionKey, Long questionId, @Size(max = 500) String subject,
                              @Size(max = 20000) String content, int keystrokes, int pasteBlocked) {
    }

    public record SessionRequest(@NotBlank String sessionKey) {
    }

    public record ProctorRequest(@NotBlank String sessionKey, @NotBlank @Size(max = 40) String type,
                                 @Size(max = 400) String detail) {
    }

    public record ProctorResult(int violations, int maxViolations, boolean counted, boolean terminated) {
    }

    /** One line of the meeting. role is AI (the customer or stakeholder) or YOU (the candidate). */
    public record Turn(String role, String text, String at) {
    }

    /** The meeting so far. audio is the newest AI line as base64 MP3, sent once, when it is new. */
    public record MeetingState(List<Turn> turns, boolean started, boolean ended, Boolean satisfied,
                               int repliesUsed, int maxReplies, String speaker, String audio) {
    }

    public record MeetingReply(@NotBlank String sessionKey, @NotBlank @Size(max = 4000) String text) {
    }

    /** What the browser needs to open the live call: a one-time secret, the model, and the meeting so far. */
    public record MeetingSession(String secret, String model, List<Turn> turns, int minReplies, int maxReplies) {
    }

    public record MeetingTranscript(@NotBlank String sessionKey, @Size(max = 60) List<Turn> turns) {
    }

    public record MeetingEnd(@NotBlank String sessionKey, boolean satisfied) {
    }

    // ------------------------------------------------------------------ admin

    public record Metric(String name, double weight, String description) {
    }

    public record QuestionDto(Long id, String code, String category, String part, int ordinal, String title,
                              String scenario, String task, List<Metric> metrics, boolean active, long timesUsed) {
    }

    public record QuestionRequest(@NotBlank String part, int ordinal, @NotBlank @Size(max = 200) String title,
                                  String scenario, @NotBlank String task, List<Metric> metrics, boolean active,
                                  @Size(max = 20) String code, @Size(max = 120) String category) {
    }

    public record PartScore(String part, String label, Double marks, double maxMarks) {
    }

    /** What a candidate is shown after the test: marks only, no comments. ready=false while marking runs. */
    public record MyResult(String status, boolean ready, Double total, Double marks, double maxMarks,
                           List<PartScore> parts) {
    }

    public record AttemptRow(Long attemptId, String name, String email, String employeeId, String team,
                             String jobRole, String status, String endReason, String startedAt, String submittedAt,
                             Integer durationSeconds, int violations, Double totalMarks, String scoring,
                             List<PartScore> parts, Double marks, Double maxMarks) {
    }

    public record Summary(int candidates, int notStarted, int completed, int inProgress, int terminated, Double averageMarks,
                          List<String> teams, Map<String, Integer> byTeam) {
    }

    public record MetricResult(String name, double weight, Double score, String comment) {
    }

    public record AnswerDetail(Long questionId, String code, String part, String title, String scenario, String task,
                               String subject, String content, int wordCount, int keystrokes, int pasteBlocked,
                               String scoreStatus, Double marks, Double maxMarks, Double percent,
                               List<MetricResult> metrics, String summary, String scoreError,
                               List<Turn> transcript, Boolean meetingSatisfied) {
    }

    public record PartTiming(String part, String label, String startedAt, String endedAt, Integer seconds) {
    }

    public record EventRow(String type, String detail, String part, boolean counted, int violationNumber,
                           String at) {
    }

    public record AttemptDetail(AttemptRow attempt, List<PartTiming> timings, List<AnswerDetail> answers,
                                List<EventRow> events, String userAgent, String ipAddress, List<ResetRow> resets) {
    }

    public record ResetRequest(@Size(max = 500) String reason) {
    }

    /** A test an admin cleared earlier, shown on the candidate's report. */
    public record ResetRow(String clearedAt, String clearedBy, String previousStatus, Double previousOverall,
                           String previousEndReason, String reason) {
    }
}
