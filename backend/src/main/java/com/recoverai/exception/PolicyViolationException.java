package com.recoverai.exception;

/**
 * Thrown if code ever attempts to execute a recovery action that the
 * Policy Engine has not explicitly allowed. This should be unreachable
 * in normal operation (the executor only runs after an ALLOWED verdict)
 * but exists as a hard safety-net guard, exercised directly in tests.
 */
public class PolicyViolationException extends RuntimeException {
    public PolicyViolationException(String message) {
        super(message);
    }
}
