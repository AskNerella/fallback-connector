package org.mule.extension.fallback.internal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for common fallback connector operations
 */
public class FallbackUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackUtils.class);
    
    /**
     * Resolve a boolean value from an object
     */
    public static boolean resolvePrimitiveBoolean(Object object) {
        if (object instanceof Boolean) {
            return (Boolean) object;
        } else if (object instanceof String && "true".equalsIgnoreCase((String) object)) {
            return true;
        }
        return false;
    }
    
    /**
     * Generate a unique circuit ID based on application name or a custom ID
     */
    public static String generateCircuitId(String customId) {
        if (customId != null && !customId.isEmpty()) {
            return customId;
        }
        return "default-circuit";
    }
}
