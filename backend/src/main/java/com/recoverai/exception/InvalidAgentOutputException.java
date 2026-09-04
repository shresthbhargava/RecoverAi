package com.recoverai.exception;

/**
 * Thrown when an LLM agent's structured output fails validation
 * (invalid enum value, out-of-range confidence, missing required field, etc).
 * Callers must treat this as a signal to fall back to deterministic logic —
 * never to retry-until-valid in a way that could be seen as "coaching" the
 * model past a safety boundary.
 */
public class InvalidAgentOutputException extends RuntimeException {
    public InvalidAgentOutputException(String message) {
        super(message);
    }

    public InvalidAgentOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
