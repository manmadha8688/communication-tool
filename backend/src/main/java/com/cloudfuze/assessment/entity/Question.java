package com.cloudfuze.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** One question, written by an admin. Metrics are the marking criteria the AI scores against. */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.cloudfuze.assessment.domain.Part part;

    private int ordinal;

    /** The id from the question bank sheet, e.g. E01 or C17. Unique, so a re-import never duplicates. */
    @Column(unique = true, length = 20)
    private String code;

    @Column(length = 120)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String scenario;

    @Column(nullable = false, columnDefinition = "text")
    private String task;

    @Column(columnDefinition = "text")
    private String metricsJson;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public com.cloudfuze.assessment.domain.Part getPart() { return part; }
    public void setPart(com.cloudfuze.assessment.domain.Part part) { this.part = part; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
