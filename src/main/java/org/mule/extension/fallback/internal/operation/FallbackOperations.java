package org.mule.extension.fallback.internal.operation;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import org.mule.extension.fallback.internal.configuration.FallbackConfiguration;
import org.mule.extension.fallback.internal.routes.FallbackFlow;
import org.mule.extension.fallback.internal.routes.MainFlow;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.parameter.ParameterResolver;
import org.mule.runtime.extension.api.runtime.process.RouterCompletionCallback;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;

/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
public class FallbackOperations {

	@DisplayName("Execute with Fallback")
	@MediaType(value = ANY, strict = false)
	public void executeWithFallback(
			@Optional(defaultValue = "60000") @DisplayName("Retry Connection (ms)") Integer retryConnection,
			
			@Optional(defaultValue = "#[output application/java --- not isEmpty(error)]") @DisplayName("Trip Condition") ParameterResolver<Object> tripCondition,

			@DisplayName("Main Flow") MainFlow flow,

			@DisplayName("Fallback Flow") FallbackFlow fallbackFlow,

			RouterCompletionCallback callback) {
		// Operation implementation goes here
		
		
	}
}
