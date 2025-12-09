package org.mule.extension.fallback.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.extension.fallback.internal.configuration.FallbackConfiguration;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.meta.JavaVersion;


/**
 * This is the main class of an extension, is the entry point from which configurations, connection providers, operations
 * and sources are going to be declared.
 */
@Extension(name = "Fallback", vendor = "K2 Partnering Solutions")
@Configurations(FallbackConfiguration.class)
@JavaVersionSupport({JavaVersion.JAVA_17})
@Xml(prefix = "fallback")
public class FallbackExtension {

}
