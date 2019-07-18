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

import io.kubernetes.client.util.Watch;
import org.sonatype.goodies.common.ComponentSupport;

import java.util.function.Consumer;

/**
 * An implementation of {@link Runnable} which monitors a Kubernetes API
 * {@link Watch} for events and then acts on those events.
 */
public class WatcherThread<T> extends ComponentSupport implements Runnable {

  private final Consumer<T> consumer;

  private final Watch<T> watch;

  private Boolean run = Boolean.TRUE;

  /**
   * Accepts a {@link Watch} and a {@link Consumer} which acts on the watched resources
   * @param watch An instance of {@link Watch} which which will be monitored for updated
   * @param consumer An implementaton of {@link Consumer} which will handle updated items
   */
  WatcherThread(Watch<T> watch, Consumer<T> consumer) {
    this.watch = watch;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    for (Watch.Response<T> response: watch) {
      if (!run) {
        break;
      }
      if (response.type.contentEquals("ADDED")) {
        consumer.accept(response.object);
      } else {
        // ONLY ADDED OPERATIONS ARE SUPPORTED!
        log.info("Watch reponse type {}} is not supported.", response.type);
      }
    }
  }

  void stop() {
    this.run = Boolean.FALSE;
  }
}
