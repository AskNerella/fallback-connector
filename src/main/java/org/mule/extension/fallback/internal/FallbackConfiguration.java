package org.mule.extension.fallback.internal;

import org.mule.extension.fallback.internal.connection.FallbackConnectionProvider;
import org.mule.extension.fallback.internal.operation.FallbackOperations;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Parameter;

/**
 * This class represents an extension configuration, values set in this class are commonly used across multiple
 * operations since they represent something core from the extension.
 */
@Operations(FallbackOperations.class)
@ConnectionProviders(FallbackConnectionProvider.class)
public class FallbackConfiguration {

  @Parameter
  private String configId;

  public String getConfigId(){
    return configId;
  }
}
