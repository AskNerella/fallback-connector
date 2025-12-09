package org.mule.extension.fallback.internal.operation;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import javax.inject.Inject;

import org.mule.extension.fallback.internal.circuit.CircuitBreakerService;
import org.mule.extension.fallback.internal.circuit.CircuitState;
import org.mule.extension.fallback.internal.routes.FallbackFlow;
import org.mule.extension.fallback.internal.routes.MainFlow;
import org.mule.extension.fallback.internal.util.FallbackUtils;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.parameter.ParameterResolver;
import org.mule.runtime.extension.api.runtime.process.RouterCompletionCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
public class FallbackOperations {

	private static final Logger LOGGER = LoggerFactory.getLogger(FallbackOperations.class);
	private static final String GET_EVENT_METHOD = "getEvent";
	
	// Singleton instance of circuit breaker service
	private static final CircuitBreakerService circuitService = new CircuitBreakerService();
	
	@Inject
	private ExpressionManager expressionManager;

	@DisplayName("Execute with Fallback")
	@MediaType(value = ANY, strict = false)
	public void executeWithFallback(
			
			@Optional(defaultValue = "default-circuit") @DisplayName("Circuit ID") String circuitId,
			
			@Optional(defaultValue = "60000") @DisplayName("Retry Connection Timeout (ms)") Integer retryConnectionTimeout,
			
			@Optional(defaultValue = "#[output application/java --- not isEmpty(error)]") @DisplayName("Trip Condition") ParameterResolver<Object> tripCondition,

			@DisplayName("Main Flow") MainFlow mainFlow,

			@DisplayName("Fallback Flow") FallbackFlow fallbackFlow,

			RouterCompletionCallback callback) {
		
		String effectiveCircuitId = FallbackUtils.generateCircuitId(circuitId);
		
		CircuitState currentState = circuitService.getState(effectiveCircuitId);
		LOGGER.debug("Circuit {} current state: {}", effectiveCircuitId, currentState);
		
		// Check if we should attempt to restore the circuit
		if (currentState == CircuitState.OPEN) {
			if (circuitService.isRetryTimeoutElapsed(effectiveCircuitId, retryConnectionTimeout.longValue())) {
				LOGGER.info("Retry timeout elapsed for circuit {}, attempting to restore", effectiveCircuitId);
				// Try to execute main flow to test if it's working
				attemptCircuitRestore(effectiveCircuitId, tripCondition, mainFlow, fallbackFlow, callback, retryConnectionTimeout);
				return;
			}
		}
		
		// Execute based on current state
		switch (currentState) {
			case CLOSED:
				executeMainFlow(effectiveCircuitId, tripCondition, mainFlow, fallbackFlow, callback, retryConnectionTimeout);
				break;
			case OPEN:
				LOGGER.info("Circuit {} is OPEN, executing fallback flow", effectiveCircuitId);
				executeFallbackFlow(fallbackFlow, callback);
				break;
			default:
				LOGGER.error("Unexpected circuit state: {}", currentState);
				executeFallbackFlow(fallbackFlow, callback);
		}
	}
	
	/**
	 * Execute main flow when circuit is closed
	 */
	private void executeMainFlow(String circuitId, 
								 ParameterResolver<Object> tripCondition,
								 MainFlow mainFlow, FallbackFlow fallbackFlow,
								 RouterCompletionCallback callback, Integer retryConnectionTimeout) {
		mainFlow.getChain().process(result -> {
			// Check if trip condition is met
			CoreEvent event = getCoreEvent(result);
			if (event != null && evaluateTripCondition(tripCondition, event)) {
				LOGGER.warn("Trip condition met for circuit {}, opening circuit", circuitId);
				handleCircuitTrip(circuitId, fallbackFlow, callback);
			} else {
				callback.success(result);
			}
		}, (error, previous) -> {
			LOGGER.error("Error in main flow for circuit {}: {}", circuitId, error.getMessage());
			// Error occurred, open the circuit and execute fallback
			handleCircuitTrip(circuitId, fallbackFlow, callback);
		});
	}
	
	/**
	 * Execute fallback flow
	 */
	private void executeFallbackFlow(FallbackFlow fallbackFlow, RouterCompletionCallback callback) {
		fallbackFlow.getChain().process(result -> {
			callback.success(result);
		}, (error, previous) -> {
			LOGGER.error("Error in fallback flow: {}", error.getMessage());
			callback.error(error);
		});
	}
	
	/**
	 * Attempt to restore circuit by testing main flow
	 */
	private void attemptCircuitRestore(String circuitId,
									   ParameterResolver<Object> tripCondition,
									   MainFlow mainFlow, FallbackFlow fallbackFlow,
									   RouterCompletionCallback callback, Integer retryConnectionTimeout) {
		mainFlow.getChain().process(result -> {
			CoreEvent event = getCoreEvent(result);
			if (event != null && evaluateTripCondition(tripCondition, event)) {
				LOGGER.warn("Trip condition still met for circuit {}, keeping circuit OPEN", circuitId);
				circuitService.recordFailureTime(circuitId);
				// Execute fallback since trip condition is still met
				executeFallbackFlow(fallbackFlow, callback);
			} else {
				LOGGER.info("Circuit {} restored to CLOSED state", circuitId);
				circuitService.setState(circuitId, CircuitState.CLOSED);
				circuitService.clearCircuitData(circuitId);
				callback.success(result);
			}
		}, (error, previous) -> {
			LOGGER.error("Circuit {} restore attempt failed: {}", circuitId, error.getMessage());
			circuitService.recordFailureTime(circuitId);
			// Execute fallback since restore failed
			executeFallbackFlow(fallbackFlow, callback);
		});
	}
	
	/**
	 * Handle circuit trip - open circuit and execute fallback
	 */
	private void handleCircuitTrip(String circuitId,
								   FallbackFlow fallbackFlow, RouterCompletionCallback callback) {
		circuitService.setState(circuitId, CircuitState.OPEN);
		circuitService.recordFailureTime(circuitId);
		LOGGER.info("Circuit {} opened due to trip condition", circuitId);
		
		// Execute fallback flow
		executeFallbackFlow(fallbackFlow, callback);
	}
	
	/**
	 * Evaluate trip condition expression
	 */
	private boolean evaluateTripCondition(ParameterResolver<Object> resolver, CoreEvent event) {
		try {
			if (resolver.getExpression().isPresent()) {
				Object result = expressionManager.evaluate(resolver.getExpression().get(), event).getValue();
				return FallbackUtils.resolvePrimitiveBoolean(result);
			}
		} catch (Exception e) {
			LOGGER.error("Error evaluating trip condition: {}", e.getMessage(), e);
		}
		return false;
	}
	
	/**
	 * Extract CoreEvent from result using reflection
	 */
	private CoreEvent getCoreEvent(Object result) {
		try {
			Method getEventMethod = result.getClass().getMethod(GET_EVENT_METHOD);
			return (CoreEvent) getEventMethod.invoke(result);
		} catch (Exception e) {
			LOGGER.debug("Could not extract CoreEvent from result: {}", e.getMessage());
			return null;
		}
	}
}
