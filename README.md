# Fallback Extension

A MuleSDK-based fallback connector that implements a circuit breaker pattern with two routes: **Main Flow** and **Fallback Flow**. This acts like a circuit breaker that automatically switches to a fallback route when the main route encounters errors.

## Overview

The Fallback Extension provides resilience to your Mule applications by implementing a circuit breaker pattern. When the main flow fails or meets a specified trip condition, the circuit opens and automatically routes requests to the fallback flow. After a configurable retry timeout, the circuit attempts to restore itself by testing the main flow.

## Features

- ✅ **Circuit Breaker Pattern**: Automatic switching between main and fallback flows
- ✅ **Self-Healing**: Attempts to restore the circuit after retry timeout
- ✅ **Configurable Trip Condition**: DataWeave expressions to control when circuit trips
- ✅ **Multiple Circuits**: Support for independent circuit breakers via Circuit ID

## Circuit States

### CLOSED (Normal Operation)
- Executes the main flow
- Monitors for errors or trip conditions
- Opens circuit when error occurs or trip condition is met

### OPEN (Circuit Breaker Active)
- Executes only the fallback flow
- Waits for retry timeout to elapse
- Attempts to restore circuit after timeout

## Installation

Add this dependency to your application pom.xml:

```xml
<dependency>
    <groupId>com.k2</groupId>
    <artifactId>fallback</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Usage

### Basic Example

```xml
<fallback:execute-with-fallback 
    circuitId="payment-api-circuit"
    retryConnectionTimeout="60000">
    
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

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `circuitId` | String | `"default-circuit"` | Unique identifier for the circuit breaker |
| `retryConnectionTimeout` | Integer | `60000` | Time in milliseconds before attempting to restore circuit (1 minute) |
| `tripCondition` | Expression | `#[output application/java --- not isEmpty(error)]` | DataWeave expression to determine when circuit should trip |
| `mainFlow` | Route | Required | The primary execution path |
| `fallbackFlow` | Route | Required | The backup execution path when circuit is open |

## Trip Condition

The trip condition is a DataWeave expression that evaluates the result of the main flow. When it evaluates to `true`, the circuit opens.

### Examples

**Check HTTP Status:**
```dataweave
#[payload.statusCode >= 500]
```

**Check Response Content:**
```dataweave
#[payload.error == true]
```

**Check for Empty Results:**
```dataweave
#[payload.customers == null or sizeOf(payload.customers) == 0]
```

**Complex Condition:**
```dataweave
#[payload.status == 'FAILED' or payload.retryCount > 3]
```

## How It Works

### Scenario 1: Main Flow Success
```
Circuit: CLOSED → Execute Main Flow → Success → Return Result → Circuit: CLOSED
```

### Scenario 2: Main Flow Failure
```
Circuit: CLOSED → Execute Main Flow → Error/Trip → Open Circuit → Execute Fallback → Circuit: OPEN
```

### Scenario 3: Circuit Open (Timeout Not Elapsed)
```
Circuit: OPEN → Check Timeout → Not Elapsed → Execute Fallback → Circuit: OPEN
```

### Scenario 4: Circuit Restoration
```
Circuit: OPEN → Check Timeout → Elapsed → Test Main Flow → Success → Close Circuit → Return Result
```

### Scenario 5: Restoration Failed
```
Circuit: OPEN → Check Timeout → Elapsed → Test Main Flow → Failed → Keep Open → Execute Fallback
```

## Best Practices

1. **Use Unique Circuit IDs**: Each API or service should have its own circuit ID
2. **Set Appropriate Timeouts**: Consider the service's typical recovery time
3. **Meaningful Trip Conditions**: Define conditions that truly indicate service degradation
4. **Monitor Circuit State**: Log circuit open/close events for observability
5. **Test Fallback Flows**: Ensure fallback logic provides acceptable alternatives

## Example Use Cases

### API Gateway with Fallback
```xml
<fallback:execute-with-fallback circuitId="external-api">
    <fallback:main-flow>
        <http:request config-ref="ExternalAPI" path="/data"/>
    </fallback:main-flow>
    <fallback:fallback-flow>
        <flow-ref name="cached-data-flow"/>
    </fallback:fallback-flow>
</fallback:execute-with-fallback>
```

### Database with Fallback
```xml
<fallback:execute-with-fallback circuitId="primary-db">
    <fallback:main-flow>
        <db:select config-ref="PrimaryDB">
            <db:sql>SELECT * FROM customers</db:sql>
        </db:select>
    </fallback:main-flow>
    <fallback:fallback-flow>
        <db:select config-ref="ReplicaDB">
            <db:sql>SELECT * FROM customers</db:sql>
        </db:select>
    </fallback:fallback-flow>
</fallback:execute-with-fallback>
```

### Service with Response Validation
```xml
<fallback:execute-with-fallback 
    circuitId="order-service"
    tripCondition="#[isEmpty(payload.orderId)]">
    <fallback:main-flow>
        <http:request config-ref="OrderService" path="/create"/>
    </fallback:main-flow>
    <fallback:fallback-flow>
        <logger message="Order service failed, queuing for retry"/>
        <jms:publish config-ref="JMS" destination="orders.retry"/>
    </fallback:fallback-flow>
</fallback:execute-with-fallback>
```

## Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd fallback

# Build and install
mvn clean install -DskipTests

# The connector will be installed to your local Maven repository
```

## Limitations

- **Single-Node**: Current implementation uses in-memory storage (not cluster-aware)
- **No Persistence**: Circuit state is lost on restart
- **No Metrics**: Does not expose circuit state metrics (can be added)
