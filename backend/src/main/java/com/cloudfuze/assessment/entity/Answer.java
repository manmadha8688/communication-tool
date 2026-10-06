package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A candidate's response to one question, autosaved while they write, then marked. */
@Entity
@Table(name = "answer", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.cloudfuze.assessment.domain.Part part;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "text")
    private String content;

    /** Meeting part only: the conversation, as JSON [{role, text, at}]. content holds the candidate's lines. */
    @Column(columnDefinition = "text")
    private String transcriptJson;

    /** Meeting part only: whether the AI participant said it was satisfied when the meeting ended. */
    private Boolean meetingSatisfied;

    private boolean meetingEnded;

    private int wordCount;

    private int keystrokes;

    private int pasteBlocked;

    private Instant savedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.cloudfuze.assessment.domain.ScoreStatus scoreStatus;

    private Double maxMarks;

    private Double marks;

    private Double percent;

    @Column(columnDefinition = "text")
    private String metricResultsJson;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(length = 500)
    private String scoreError;

    private Instant scoredAt;

    public Long getId() { return id; }
    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public Boolean getMeetingSatisfied() { return meetingSatisfied; }
    public void setMeetingSatisfied(Boolean meetingSatisfied) { this.meetingSatisfied = meetingSatisfied; }
    public boolean isMeetingEnded() { return meetingEnded; }
    public void setMeetingEnded(boolean meetingEnded) { this.meetingEnded = meetingEnded; }
    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public com.cloudfuze.assessment.domain.Part getPart() { return part; }
    public void setPart(com.cloudfuze.assessment.domain.Part part) { this.part = part; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public int getKeystrokes() { return keystrokes; }
    public void setKeystrokes(int keystrokes) { this.keystrokes = keystrokes; }
    public int getPasteBlocked() { return pasteBlocked; }
    public void setPasteBlocked(int pasteBlocked) { this.pasteBlocked = pasteBlocked; }
    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
    public com.cloudfuze.assessment.domain.ScoreStatus getScoreStatus() { return scoreStatus; }
    public void setScoreStatus(com.cloudfuze.assessment.domain.ScoreStatus scoreStatus) { this.scoreStatus = scoreStatus; }
    public Double getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Double maxMarks) { this.maxMarks = maxMarks; }
    public Double getMarks() { return marks; }
    public void setMarks(Double marks) { this.marks = marks; }
    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }
    public String getMetricResultsJson() { return metricResultsJson; }
    public void setMetricResultsJson(String metricResultsJson) { this.metricResultsJson = metricResultsJson; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getScoreError() { return scoreError; }
    public void setScoreError(String scoreError) { this.scoreError = scoreError; }
    public Instant getScoredAt() { return scoredAt; }
    public void setScoredAt(Instant scoredAt) { this.scoredAt = scoredAt; }
}
