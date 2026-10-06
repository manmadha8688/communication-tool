package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A test an admin cleared so the candidate could sit it again. The attempt itself is deleted, so
 * this row is the only record that it existed: who sat it, how it went, who cleared it and why.
 */
@Entity
@Table(name = "attempt_reset")
public class AttemptReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String candidateEmail;
    private String candidateName;
    @Column(length = 20)
    private String previousStatus;
    private Double previousOverall;
    @Column(length = 200)
    private String previousEndReason;
    private Instant previousStartedAt;
    @Column(nullable = false, length = 320)
    private String clearedBy;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false)
    private Instant clearedAt;

    public Long getId() { return id; }
    public String getCandidateEmail() { return candidateEmail; }
    public void setCandidateEmail(String v) { this.candidateEmail = v; }
    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String v) { this.candidateName = v; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String v) { this.previousStatus = v; }
    public Double getPreviousOverall() { return previousOverall; }
    public void setPreviousOverall(Double v) { this.previousOverall = v; }
    public String getPreviousEndReason() { return previousEndReason; }
    public void setPreviousEndReason(String v) { this.previousEndReason = v; }
    public Instant getPreviousStartedAt() { return previousStartedAt; }
    public void setPreviousStartedAt(Instant v) { this.previousStartedAt = v; }
    public String getClearedBy() { return clearedBy; }
    public void setClearedBy(String v) { this.clearedBy = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public Instant getClearedAt() { return clearedAt; }
    public void setClearedAt(Instant v) { this.clearedAt = v; }
}
