package org.mule.extension.fallback.internal.configuration;

import org.mule.extension.fallback.internal.operation.FallbackOperations;
import org.mule.runtime.extension.api.annotation.Operations;

/**
 * This class represents an extension configuration, values set in this class are commonly used across multiple
 * operations since they represent something core from the extension.
 */
@Operations(FallbackOperations.class)
public class FallbackConfiguration {
	
}
