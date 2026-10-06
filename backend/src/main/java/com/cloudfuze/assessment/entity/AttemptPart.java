package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** When one part of an attempt began and ended. The deadline is fixed when the part starts. */
@Entity
@Table(name = "attempt_part", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "part"}))
public class AttemptPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.cloudfuze.assessment.domain.Part part;

    private Instant startedAt;

    private Instant endedAt;

    private Instant deadline;

    public Long getId() { return id; }
    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }
    public com.cloudfuze.assessment.domain.Part getPart() { return part; }
    public void setPart(com.cloudfuze.assessment.domain.Part part) { this.part = part; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
}
