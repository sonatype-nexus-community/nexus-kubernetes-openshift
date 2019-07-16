package com.redhat.labs.nexus.openshift.internal;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class WatcherThread implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(WatcherThread.class);

  private final Watch<V1ConfigMap> watcher;
  private final Consumer<V1ConfigMap> consumer;

  public WatcherThread(Watch<V1ConfigMap> configMapWatcher, Consumer<V1ConfigMap> consumer) {
    this.watcher = configMapWatcher;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    for(Watch.Response<V1ConfigMap> item: watcher) {
      switch(item.type) {
        case "ADDED":
          consumer.accept(item.object);
          break;
        default:
          LOG.info("This plugin will ONLY CREATE resources, it cannot modify or delete them.");
      }
    }
  }
}
