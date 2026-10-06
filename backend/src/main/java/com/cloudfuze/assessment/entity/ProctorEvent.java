package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** One thing the browser reported during the test: a tab switch, a blocked paste, leaving full screen. */
@Entity
@Table(name = "proctor_event")
public class ProctorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 400)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.cloudfuze.assessment.domain.Part part;

    private boolean counted;

    private int violationNumber;

    @Column(name = "occurred_at", nullable = false)
    private Instant at;

    public Long getId() { return id; }
    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public com.cloudfuze.assessment.domain.Part getPart() { return part; }
    public void setPart(com.cloudfuze.assessment.domain.Part part) { this.part = part; }
    public boolean isCounted() { return counted; }
    public void setCounted(boolean counted) { this.counted = counted; }
    public int getViolationNumber() { return violationNumber; }
    public void setViolationNumber(int violationNumber) { this.violationNumber = violationNumber; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
