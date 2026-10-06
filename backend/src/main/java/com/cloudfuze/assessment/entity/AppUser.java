package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A person who has signed in. The profile fields are what the candidate typed before starting. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    private String microsoftName;

    private String name;

    @Column(length = 64)
    private String employeeId;

    @Column(length = 120)
    private String team;

    @Column(length = 120)
    private String jobRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.cloudfuze.assessment.domain.Role role;

    private boolean profileComplete;

    private Instant createdAt;

    private Instant lastLoginAt;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMicrosoftName() { return microsoftName; }
    public void setMicrosoftName(String microsoftName) { this.microsoftName = microsoftName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    public com.cloudfuze.assessment.domain.Role getRole() { return role; }
    public void setRole(com.cloudfuze.assessment.domain.Role role) { this.role = role; }
    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
