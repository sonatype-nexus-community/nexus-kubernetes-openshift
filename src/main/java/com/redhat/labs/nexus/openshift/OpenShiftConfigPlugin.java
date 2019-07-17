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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.base.BaseOperation;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.goodies.lifecycle.LifecycleSupport;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.user.UserNotFoundException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static com.redhat.labs.nexus.openshift.BlobStoreConfigWatcher.addBlobStore;
import static com.redhat.labs.nexus.openshift.RepositoryConfigWatcher.createNewRepository;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.SERVICES;

@Named(OpenShiftConfigPlugin.TYPE)
@Singleton
@ManagedLifecycle(phase = SERVICES)
public class OpenShiftConfigPlugin extends LifecycleSupport {
  static final String TYPE = "openshift-kubernetes-plugin";

  @Inject
  BlobStoreManager blobStoreManager;

  @Inject
  RepositoryApi repository;

  @Inject
  SecuritySystem security;

  private OpenShiftClient client;

  private List<Watch> watchers = new ArrayList<>();

  public OpenShiftConfigPlugin() {
    log.info("OpenShift/Kubernetes Plugin loading");
  }

  @Override
  protected void doStart() throws Exception {
    // This supports both stock K8s AND OpenShift so we don't have to use one or the other.
    // If running in OpenShift or K8s, it will automatically detect the correct settings
    // and service account credentials from the /run/secrets/kubernetes.io/serviceaccount
    // directory
    log.info("OpenShift/Kubernetes Plugin starting");
    client = new DefaultOpenShiftClient();
    configureFromCluster();
  }

  void configureFromCluster() throws Exception {
    try {
      client.settings().getNamespace();
      log.info("OpenShift/Kubernetes client successfully configured");
      setAdminPassword();

      readAndConfigure();

      configureWatchers();
    } catch (IllegalStateException ise) {
      log.warn("OpenShift/Kubernetes client could not be configured", ise);
      throw new Exception("Unable to configure k8s/OpenShift client", ise);
    }
  }

  void readAndConfigure() {
    client.configMaps().withLabel("nexus-type==blobstore").list().getItems().forEach(blobStore -> addBlobStore(blobStore, blobStoreManager));

    client.configMaps().withLabel("nexus-type==repository").list().getItems().forEach(repoConfig -> createNewRepository(repository, repoConfig));
  }

  void configureWatchers() {
    Watch blobStoreConfigWatcher = client.configMaps().withLabel("nexus-type==blobstore").watch(new BlobStoreConfigWatcher(blobStoreManager));

    Watch repoConfigWatcher = client.configMaps().withLabel("nexus-type==repository").watch(new RepositoryConfigWatcher(repository, blobStoreManager));
    watchers.add(blobStoreConfigWatcher);
    watchers.add(repoConfigWatcher);
  }

  void setAdminPassword() {
    try {
      String password = client.secrets().withName("nexus").get().getData().getOrDefault("password", System.getenv().getOrDefault("NEXUS_PASSWORD", "admin123"));
      security.changePassword("admin", password);
    } catch (UserNotFoundException unfe) {
      log.warn("User 'admin' not found, unable to set password", unfe);
    } catch (Exception e) {
      log.warn("An error occurred while retrieving Secrets from OpenShift");
    }
  }

  @Override
  protected void doStop() throws Exception {
    watchers.forEach(Watch::close);
    client.close();
  }
}
