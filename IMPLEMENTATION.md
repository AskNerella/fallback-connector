# Fallback Connector Implementation

## Overview
This is a MuleSDK-based fallback connector that implements a circuit breaker pattern with two routes:
- **Main Flow**: The primary execution path
- **Fallback Flow**: The backup execution path when the circuit is open

## Circuit Breaker Pattern

### States
1. **CLOSED**: Normal operation - main flow is executed
2. **OPEN**: Circuit is open - only fallback flow is executed

### Behavior

#### When Circuit is CLOSED
- Executes the main flow
- If an error occurs OR trip condition is met:
  - Opens the circuit (sets state to OPEN)
  - Records the failure time
  - Executes the fallback flow

#### When Circuit is OPEN
- Executes only the fallback flow
- After retry connection timeout elapses:
  - Attempts to execute main flow to test if it's working
  - If trip condition is NOT met:
    - Restores circuit to CLOSED state
    - Clears circuit data
  - If trip condition is still met or error occurs:
    - Keeps circuit OPEN
    - Records new failure time
    - Executes fallback flow

## Operation Parameters

### Execute with Fallback
- **Circuit ID**: Unique identifier for the circuit breaker (default: "default-circuit")
- **Retry Connection Timeout (ms)**: Time in milliseconds before attempting to restore the circuit (default: 60000 = 1 minute)
- **Trip Condition**: DataWeave expression that evaluates to true when the circuit should trip (default: checks if error is not empty)
- **Main Flow**: The primary flow to execute
- **Fallback Flow**: The backup flow to execute when circuit is open

## Implementation Details

### CircuitBreakerService
- Manages circuit state using in-memory storage (ConcurrentHashMap)
- In production, this should be replaced with ObjectStore injection for persistence across deployments
- Tracks:
  - Circuit state (CLOSED/OPEN)
  - Last failure time for retry timeout calculation

### FallbackOperations
- Main operation: `executeWithFallback`
- Implements the circuit breaker logic:
  - State management
  - Error handling
  - Fallback routing
  - Circuit restoration attempts

### Utility Classes
- **FallbackUtils**: Helper methods for boolean resolution and circuit ID generation

## Key Features

1. **Automatic Circuit Opening**: When main flow fails or trip condition is met
2. **Automatic Retry**: After configured timeout, attempts to restore circuit
3. **Smart Fallback**: Always executes fallback when circuit is open
4. **DataWeave Trip Condition**: Flexible condition evaluation using DataWeave expressions

## Example Usage

```xml
<fallback:execute-with-fallback 
    circuitId="my-api-circuit"
    retryConnectionTimeout="120000"
    tripCondition="#[output application/java --- not isEmpty(error)]">
    
    <fallback:main-flow>
        <!-- Your main flow logic here -->
        <http:request config-ref="API_Config" path="/main-api"/>
    </fallback:main-flow>
    
    <fallback:fallback-flow>
        <!-- Your fallback logic here -->
        <http:request config-ref="API_Config" path="/fallback-api"/>
    </fallback:fallback-flow>
    
</fallback:execute-with-fallback>
```

## Notes

- The current implementation uses in-memory storage (ConcurrentHashMap) for simplicity
- For production use with clustering, replace CircuitBreakerService storage with ObjectStore injection
- No cluster mode is implemented - this is a simple, single-node implementation
- Trip condition is evaluated using DataWeave expressions for maximum flexibility

## Building the Connector

```bash
mvn clean install
```

## Dependencies

- Mule SDK API 0.10.0
- SLF4J 1.7.32
- Java 17

## References

Based on the circuit breaker pattern from: https://github.com/Vishwasp13/circuit-breaker-module
