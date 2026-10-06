package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** The single sitting a candidate is allowed. One row per user, enforced by the unique key. */
@Entity
@Table(name = "attempt")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.cloudfuze.assessment.domain.AttemptStatus status;

    @Column(nullable = false, length = 100)
    private String partOrder;

    private int partIndex;

    @Column(length = 64)
    private String sessionKey;

    private int violations;

    @Column(length = 200)
    private String endReason;

    private Double totalMarks;

    private boolean scoringDone;

    @Column(length = 400)
    private String userAgent;

    @Column(length = 64)
    private String ipAddress;

    private Instant startedAt;

    private Instant submittedAt;

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public com.cloudfuze.assessment.domain.AttemptStatus getStatus() { return status; }
    public void setStatus(com.cloudfuze.assessment.domain.AttemptStatus status) { this.status = status; }
    public String getPartOrder() { return partOrder; }
    public void setPartOrder(String partOrder) { this.partOrder = partOrder; }
    public int getPartIndex() { return partIndex; }
    public void setPartIndex(int partIndex) { this.partIndex = partIndex; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    public int getViolations() { return violations; }
    public void setViolations(int violations) { this.violations = violations; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public Double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Double totalMarks) { this.totalMarks = totalMarks; }
    public boolean isScoringDone() { return scoringDone; }
    public void setScoringDone(boolean scoringDone) { this.scoringDone = scoringDone; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
