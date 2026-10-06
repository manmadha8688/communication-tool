package com.cloudfuze.assessment.domain;

/** IN_PROGRESS while being sat; SUBMITTED when finished normally or by time; TERMINATED on the third violation. */
public enum AttemptStatus { IN_PROGRESS, SUBMITTED, TERMINATED }
