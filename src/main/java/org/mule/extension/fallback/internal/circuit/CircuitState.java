package org.mule.extension.fallback.internal.circuit;

/**
 * Circuit breaker states
 * CLOSED: Normal operation, main flow is executed
 * OPEN: Circuit is open, only fallback flow is executed
 */
public enum CircuitState {
    CLOSED,
    OPEN
}
