package com.redhat.labs.nexus.openshift;

/*-
 * #%L
 * com.redhat.labs.nexus:nexus-openshift-plugin
 * %%
 * Copyright (C) 2008 - 2019 Red Hat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
