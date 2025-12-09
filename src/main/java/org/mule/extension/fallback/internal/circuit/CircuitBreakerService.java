package org.mule.extension.fallback.internal.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage circuit breaker state using in-memory storage
 * In production, this would be replaced with ObjectStore injection
 */
public class CircuitBreakerService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerService.class);
    private static final String STATE_KEY_PREFIX = "circuit_state_";
    private static final String LAST_FAILURE_TIME_PREFIX = "last_failure_";
    
    // Using ConcurrentHashMap for thread-safe operations
    // In production, replace with ObjectStore
    private final Map<String, Object> storage = new ConcurrentHashMap<>();
    
    /**
     * Get current circuit state
     */
    public CircuitState getState(String circuitId) {
        String key = STATE_KEY_PREFIX + circuitId;
        Object value = storage.get(key);
        if (value instanceof String) {
            return CircuitState.valueOf((String) value);
        }
        // Default state is CLOSED
        return CircuitState.CLOSED;
    }
    
    /**
     * Update circuit state
     */
    public void setState(String circuitId, CircuitState state) {
        String key = STATE_KEY_PREFIX + circuitId;
        storage.put(key, state.name());
        LOGGER.info("Circuit {} state changed to {}", circuitId, state);
    }
    
    /**
     * Record failure time
     */
    public void recordFailureTime(String circuitId) {
        String key = LAST_FAILURE_TIME_PREFIX + circuitId;
        storage.put(key, System.currentTimeMillis());
        LOGGER.debug("Recorded failure time for circuit {}", circuitId);
    }
    
    /**
     * Get last failure time
     */
    public Long getLastFailureTime(String circuitId) {
        String key = LAST_FAILURE_TIME_PREFIX + circuitId;
        Object value = storage.get(key);
        if (value instanceof Long) {
            return (Long) value;
        }
        return null;
    }
    
    /**
     * Check if retry timeout has elapsed
     */
    public boolean isRetryTimeoutElapsed(String circuitId, long retryConnectionTimeout) {
        Long lastFailureTime = getLastFailureTime(circuitId);
        if (lastFailureTime == null) {
            return true; // No failure recorded, timeout has elapsed
        }
        long elapsedTime = System.currentTimeMillis() - lastFailureTime;
        boolean elapsed = elapsedTime >= retryConnectionTimeout;
        LOGGER.debug("Circuit {} retry timeout check: elapsed={}ms, required={}ms, result={}", 
                    circuitId, elapsedTime, retryConnectionTimeout, elapsed);
        return elapsed;
    }
    
    /**
     * Clear circuit state data
     */
    public void clearCircuitData(String circuitId) {
        String stateKey = STATE_KEY_PREFIX + circuitId;
        String timeKey = LAST_FAILURE_TIME_PREFIX + circuitId;
        storage.remove(stateKey);
        storage.remove(timeKey);
        LOGGER.debug("Cleared circuit data for {}", circuitId);
    }
}
