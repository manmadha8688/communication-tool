package com.cloudfuze.assessment.service;

/** Published when an attempt ends, however it ended; marking starts once the transaction commits. */
public record AttemptFinished(Long attemptId) {
}
