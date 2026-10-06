package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** One admin, by email. Whoever is in this table gets the admin portal at their next request. */
@Entity
@Table(name = "admin_email")
public class AdminEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    private Instant addedAt;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
