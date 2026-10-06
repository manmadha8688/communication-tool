package com.cloudfuze.assessment.domain;

/** The three sections, in the order they are sat. */
public enum Part {
    TEAMS("Teams message"),
    EMAIL("Email"),
    MEETING("Meeting");

    private final String label;

    Part(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
