package com.redhat.labs.nexus.openshift;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.util.Watch;
import org.sonatype.goodies.common.ComponentSupport;

import java.util.function.Consumer;

/**
 * An implementation of {@link Runnable} which monitors a Kubernetes API watch
 * for events and then acts on those events.
 */
public class WatcherThread extends ComponentSupport implements Runnable {

  final Consumer<V1ConfigMap> consumer;

  final Watch<V1ConfigMap> watch;

  private Boolean run = Boolean.TRUE;

  public WatcherThread(Watch<V1ConfigMap> watch, Consumer<V1ConfigMap> consumer) {
    this.watch = watch;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    for (Watch.Response<V1ConfigMap> response: watch) {
      if (!run.booleanValue()) {
        break;
      }
      if (response.type == "ADDED") {
        consumer.accept(response.object);
      } else {
        // ONLY ADDED OPERATIONS ARE SUPPORTED!
        log.info(String.format("Watch reponse type %s is not supported.", response.type));
      }
    }
  }

  public void stop() {
    this.run = Boolean.FALSE;
  }
}
