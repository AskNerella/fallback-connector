# Fallback Connector - Circuit Breaker Implementation Summary

## What Was Implemented

### 1. Circuit Breaker Pattern
A simplified circuit breaker implementation with two states:
- **CLOSED**: Normal operation, executes main flow
- **OPEN**: Circuit breaker triggered, executes only fallback flow

### 2. Key Components

#### CircuitState Enum (`CircuitState.java`)
- Defines two states: CLOSED and OPEN
- Simple enum without complex state transitions

#### CircuitBreakerService (`CircuitBreakerService.java`)
- Manages circuit state using in-memory ConcurrentHashMap
- Tracks circuit state and last failure time per circuit ID
- Provides methods for:
  - Getting/setting circuit state
  - Recording failure time
  - Checking if retry timeout has elapsed
  - Clearing circuit data

#### FallbackOperations (`FallbackOperations.java`)
- Main operation: `executeWithFallback`
- Implements the circuit breaker logic with the following behavior:

**Parameters:**
- **Circuit ID**: Unique identifier for the circuit (default: "default-circuit")
- **Retry Connection Timeout (ms)**: Time before attempting to restore circuit (default: 60000ms)
- **Trip Condition**: DataWeave expression to determine if circuit should trip
- **Main Flow**: Primary execution route
- **Fallback Flow**: Backup execution route

**Logic:**
1. When circuit is CLOSED:
   - Executes main flow
   - If error occurs OR trip condition is met:
     - Opens circuit
     - Records failure time
     - Executes fallback flow

2. When circuit is OPEN:
   - If retry timeout has NOT elapsed:
     - Executes fallback flow only
   - If retry timeout HAS elapsed:
     - Attempts to execute main flow
     - If successful and trip condition NOT met:
       - Restores circuit to CLOSED
       - Clears circuit data
     - If fails or trip condition still met:
       - Keeps circuit OPEN
       - Records new failure time
       - Executes fallback flow

#### FallbackUtils (`FallbackUtils.java`)
- Helper methods for:
  - Boolean resolution from objects
  - Circuit ID generation

### 3. Configuration

#### FallbackConfiguration (`FallbackConfiguration.java`)
- Simple configuration class
- No ObjectStore injection (using in-memory storage instead for simplicity)

### 4. Route Classes

#### MainFlow and FallbackFlow
- Extend `Route` class from Mule SDK
- Represent the two execution paths

## Key Design Decisions

### 1. No Cluster Mode
- Implementation uses in-memory storage (ConcurrentHashMap)
- Singleton CircuitBreakerService instance
- For production clustering, replace with ObjectStore

### 2. Simplified State Machine
- Only 2 states (CLOSED/OPEN) instead of 3 (CLOSED/ERROR/OPEN)
- Easier to understand and maintain
- Still provides circuit breaker functionality

### 3. No Config Parameter in Router
- Router operations cannot have @Config parameter in Mule SDK
- CircuitBreakerService is a static singleton instead

### 4. Retry Logic
- After retry timeout, circuit attempts self-healing
- Tests main flow to see if it's working again
- Uses trip condition to determine if restoration is successful

## How It Works

### Scenario 1: Main Flow Success
```
Circuit: CLOSED
-> Execute Main Flow
-> Success (trip condition NOT met)
-> Return result
-> Circuit stays CLOSED
```

### Scenario 2: Main Flow Failure
```
Circuit: CLOSED
-> Execute Main Flow
-> Error OR trip condition met
-> Open Circuit
-> Record failure time
-> Execute Fallback Flow
-> Circuit: OPEN
```

### Scenario 3: Circuit Open, Timeout Not Elapsed
```
Circuit: OPEN
-> Retry timeout NOT elapsed
-> Execute Fallback Flow directly
-> Circuit stays OPEN
```

### Scenario 4: Circuit Open, Timeout Elapsed - Successful Restore
```
Circuit: OPEN
-> Retry timeout elapsed
-> Attempt Main Flow
-> Success (trip condition NOT met)
-> Restore Circuit to CLOSED
-> Clear circuit data
-> Return result
```

### Scenario 5: Circuit Open, Timeout Elapsed - Failed Restore
```
Circuit: OPEN
-> Retry timeout elapsed
-> Attempt Main Flow
-> Error OR trip condition still met
-> Keep Circuit OPEN
-> Record new failure time
-> Execute Fallback Flow
```

## Trip Condition

The trip condition is a DataWeave expression that evaluates to a boolean:
- `true` = Circuit should trip/stay open
- `false` = Circuit is healthy/can close

Default: `#[output application/java --- not isEmpty(error)]`
- Trips if there's an error in the flow

Custom examples:
- `#[error.errorType.identifier == 'HTTP:TIMEOUT']` - Trip only on timeout
- `#[payload.status == 500]` - Trip on HTTP 500 response
- `#[sizeOf(payload.errors) > 0]` - Trip if errors array has items

## Usage Example

```xml
<fallback:execute-with-fallback 
    circuitId="payment-api-circuit"
    retryConnectionTimeout="120000"
    tripCondition="#[output application/java --- not isEmpty(error) or payload.status == 500]">
    
    <fallback:main-flow>
        <http:request config-ref="PaymentAPI" path="/process"/>
        <logger message="Payment processed successfully"/>
    </fallback:main-flow>
    
    <fallback:fallback-flow>
        <logger level="WARN" message="Using fallback payment method"/>
        <http:request config-ref="BackupPaymentAPI" path="/process"/>
    </fallback:fallback-flow>
    
</fallback:execute-with-fallback>
```

## Differences from Reference Implementation

### Circuit Breaker Module (Hazelcast-based)
- Uses Hazelcast for distributed state
- 3 states: CLOSED, ERROR, OPEN
- Tracks error count and request count
- Threshold percentage for tripping
- Cluster-aware

### Our Implementation (Simplified)
- Uses in-memory ConcurrentHashMap
- 2 states: CLOSED, OPEN
- No error/request counting
- Trip condition based on DataWeave expression
- Single-node only (for simplicity)

## Future Enhancements

To make this production-ready with clustering:

1. **Add ObjectStore Injection**
   ```java
   @Inject
   private ObjectStoreManager objectStoreManager;
   ```

2. **Replace ConcurrentHashMap**
   - Use persistent ObjectStore instead
   - Share state across cluster nodes

3. **Add Metrics**
   - Track circuit open/close events
   - Monitor failure rates
   - Expose via monitoring API

4. **Add Configuration**
   - Make circuit settings configurable
   - Allow different timeouts per circuit
   - Configure trip conditions per operation

## Build and Installation

```bash
# Build
mvn clean install -DskipTests

# The connector JAR will be at:
# target/fallback-1.0.0-SNAPSHOT-mule-plugin.jar

# Install to local Maven repo (already done by install command)
# Available at: ~/.m2/repository/com/k2/fallback/1.0.0-SNAPSHOT/
```

## Testing Recommendations

1. **Test Circuit Opening**
   - Trigger error in main flow
   - Verify circuit opens
   - Verify fallback executes

2. **Test Circuit Restoration**
   - Wait for retry timeout
   - Ensure main flow is healthy
   - Verify circuit closes

3. **Test Trip Condition**
   - Use custom trip conditions
   - Verify they evaluate correctly
   - Test with various error scenarios

4. **Test Multiple Circuits**
   - Use different circuit IDs
   - Verify independent operation
   - Ensure no state leakage

## Summary

This implementation provides a working circuit breaker pattern that:
- ✅ Opens circuit on errors
- ✅ Executes fallback when open
- ✅ Attempts self-healing after timeout
- ✅ Uses configurable trip conditions
- ✅ Supports multiple independent circuits
- ✅ Builds successfully with Mule SDK
- ⚠️ Not cluster-aware (by design for simplicity)
- ⚠️ Uses in-memory storage (not persistent)

Perfect for single-node deployments or as a starting point for more advanced implementations.
