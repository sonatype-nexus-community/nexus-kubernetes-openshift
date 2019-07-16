package com.redhat.labs.nexus.openshift;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.log.LogConfigurationCustomizer;
import org.sonatype.nexus.common.log.LoggerLevel;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class LogConfigurationCustomizerImpl extends ComponentSupport implements LogConfigurationCustomizer {

  @Override
  public void customize(Configuration configuration) {
    configuration.setLoggerLevel("com.redhat.labs.nexus", LoggerLevel.DEBUG);
  }
}
