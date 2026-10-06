package com.cloudfuze.assessment.domain;

/** Marking state of one answer. FAILED means the AI could not mark it; an admin can re-run it. */
public enum ScoreStatus { PENDING, SCORED, FAILED }
