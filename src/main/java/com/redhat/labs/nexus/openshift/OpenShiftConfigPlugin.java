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

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import org.sonatype.goodies.lifecycle.LifecycleSupport;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.user.UserNotFoundException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.redhat.labs.nexus.openshift.BlobStoreConfigWatcher.addBlobStore;
import static com.redhat.labs.nexus.openshift.RepositoryConfigWatcher.createNewRepository;
import static java.util.concurrent.TimeUnit.SECONDS;
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

  private ApiClient client;
  private CoreV1Api api;
  private String namespace;
  private List<WatcherThread> watchers = new ArrayList<>();

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
    File namespaceFile = new File("/run/secrets/kubernetes.io/serviceaccount/namespace");
    namespace = null;
    if (namespaceFile.exists() && namespaceFile.canRead()) {
      try {
        namespace = new String(Files.readAllBytes(namespaceFile.toPath()), Charset.defaultCharset()).trim();
      } catch (IOException ioe) {
        log.warn("Unable to read namespace from running container", ioe);
        namespace = System.getenv("KUBERNETES_NAMESPACE");
        if (namespace == null) {
          log.warn("Unable to read namespace from environment variable KUBERNETES_NAMESPACE");
        }
      }
    }
    client = Config.defaultClient();
    api = new CoreV1Api(client);
    configureFromCluster();
  }

  void configureFromCluster() throws Exception {
    try {
      client.getBasePath();
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
    try {
      api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==blobstore", null, null, null, Boolean.FALSE)
          .getItems()
          .forEach(configMap -> addBlobStore(configMap, blobStoreManager));

      api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE)
          .getItems()
          .forEach(configMap -> createNewRepository(repository, configMap));
    } catch (ApiException e) {
      log.error("Error reading ConfigMaps", e);
    }
  }

  void configureWatchers() {
    try {
      client.getHttpClient().setReadTimeout(0, SECONDS);
      Watch<V1ConfigMap> blobstoreWatch = Watch.createWatch(client, api.listNamespacedConfigMapCall(namespace, null, null, null, null,
          "nexus-type==blobstore", null, null, null, Boolean.TRUE, null, null), new TypeToken<Watch.Response<V1ConfigMap>>() {}.getType());

      Consumer<V1ConfigMap> blobstoreConsumer = configMap -> BlobStoreConfigWatcher.addBlobStore(configMap, blobStoreManager);

      WatcherThread blobStoreWatcherThread = new WatcherThread(blobstoreWatch, blobstoreConsumer);

      Watch<V1ConfigMap> repositoryWatch = Watch.createWatch(client, api.listNamespacedConfigMapCall(namespace, null, null, null, null,
          "nexus-type==repository", null, null, null, Boolean.TRUE, null, null), new TypeToken<Watch.Response<V1ConfigMap>>() {}.getType());

      Consumer<V1ConfigMap> repositoryConsumer = configMap -> RepositoryConfigWatcher.createNewRepository(repository, configMap);

      WatcherThread repoWatcherThread = new WatcherThread(repositoryWatch, repositoryConsumer);

      watchers.add(blobStoreWatcherThread);
      watchers.add(repoWatcherThread);
      ForkJoinPool.commonPool().execute(blobStoreWatcherThread);
      ForkJoinPool.commonPool().execute(repoWatcherThread);
    } catch (ApiException e) {
      log.error("Unable to configure watcher threads for ConfigMaps.", e);
    }
  }

  void setAdminPassword() {
    try {
      V1Secret nexusSecret = api.readNamespacedSecret("nexus", "namespace", null, null, null);
      String password = new String(nexusSecret.getData().getOrDefault("password".getBytes(), System.getenv().getOrDefault("NEXUS_PASSWORD", "admin123").getBytes()), Charset.defaultCharset());
      security.changePassword("admin", password);
    } catch (UserNotFoundException unfe) {
      log.warn("User 'admin' not found, unable to set password", unfe);
    } catch (Exception e) {
      log.warn("An error occurred while retrieving Secrets from OpenShift");
    }
  }

  @Override
  protected void doStop() throws Exception {
    watchers.forEach(watcher -> watcher.stop());
    api = null;
    client = null;
  }
}
