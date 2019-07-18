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
import com.squareup.okhttp.Call;
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
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.SERVICES;

/**
 * Entrypoint for this plugin... When started, the plugin will create a Kubernetes
 * API client which will automatically try to configure itself. Once the client
 * is created, the client will be used to pull Secrets and ConfigMaps from the K8s
 * API in order to provision blobstores, repositories, and the admin password.
 */
@Named(OpenShiftConfigPlugin.TYPE)
@Singleton
@ManagedLifecycle(phase = SERVICES)
public class OpenShiftConfigPlugin extends LifecycleSupport {
  static final String TYPE = "openshift-kubernetes-plugin";
  private static final String SERVICE_ACCOUNT_NAMESPACE_FILE = "/run/secrets/kubernetes.io/serviceaccount/namespace";

  @Inject
  BlobStoreManager blobStoreManager;

  @Inject
  RepositoryApi repository;

  @Inject
  SecuritySystem security;

  BlobStoreConfigWatcher blobStoreConfigWatcher;

  RepositoryConfigWatcher repositoryConfigWatcher;

  ApiClient client;
  CoreV1Api api;
  String namespace;
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
    repositoryConfigWatcher = new RepositoryConfigWatcher(repository, blobStoreManager);
    blobStoreConfigWatcher = new BlobStoreConfigWatcher(blobStoreManager);
    log.info("OpenShift/Kubernetes Plugin starting");
    File namespaceFile = new File(SERVICE_ACCOUNT_NAMESPACE_FILE);
    namespace = null;
    if (namespaceFile.exists() && namespaceFile.canRead()) {
      try {
        namespace = new String(Files.readAllBytes(namespaceFile.toPath()), Charset.defaultCharset()).trim();
        log.debug("Read namespace from filesystem");
      } catch (IOException ioe) {
        log.warn("Unable to read namespace from running container", ioe);
        namespace = System.getenv("KUBERNETES_NAMESPACE");
      }
    }
    if (namespace == null) {
      log.warn("Unable to read namespace from environment variable KUBERNETES_NAMESPACE");
    } else {
      log.debug("Detected Namespace: {}", namespace);
      client = Config.defaultClient();
      api = new CoreV1Api(client);
      configureFromCluster();
    }
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
          .forEach(configMap -> blobStoreConfigWatcher.addBlobStore(configMap, blobStoreManager));

      api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE)
          .getItems()
          .forEach(configMap -> repositoryConfigWatcher.createNewRepository(repository, configMap));
    } catch (ApiException e) {
      log.error("Error reading ConfigMaps", e);
    }
  }

  Watch<V1ConfigMap> buildWatcher(String labelSelector) throws ApiException {
    Call blobWatchCall = api.listNamespacedConfigMapCall(namespace, null, null, null, null,
        labelSelector, null, null, null, Boolean.TRUE, null, null);
    return Watch.createWatch(client, blobWatchCall, new TypeToken<Watch.Response<V1ConfigMap>>() {}.getType());
  }

  void addWatcher(String labelSelector, Consumer<V1ConfigMap> consumer) throws ApiException {
    WatcherThread watcherThread = new WatcherThread(buildWatcher(labelSelector), consumer);
    watchers.add(watcherThread);
    ForkJoinPool.commonPool().execute(watcherThread);
  }

  void configureWatchers() {
    try {
      client.getHttpClient().setReadTimeout(0, SECONDS);
      addWatcher("nexus-type==repository", configMap -> repositoryConfigWatcher.createNewRepository(repository, configMap));
      addWatcher("nexus-type=blobstore", configMap -> blobStoreConfigWatcher.addBlobStore(configMap, blobStoreManager));
    } catch (ApiException e) {
      log.error("Unable to configure watcher threads for ConfigMaps.", e);
    }
  }

  void setAdminPassword() {
    log.debug("Entering setAdminPassword");
    try {
      V1Secret nexusSecret = api.readNamespacedSecret("nexus", namespace, null, null, null);
      if (nexusSecret != null) {
        log.debug("V1Secret retrieved");
        Map<String, byte[]> secretData = nexusSecret.getData();
        log.debug("Displaying keys and values from Secret");
        secretData.keySet().stream().forEach(s -> log.debug(String.format("%s:%s", s, new String(secretData.get(s)))));
        String password = new String(secretData.getOrDefault("password", System.getenv().getOrDefault("NEXUS_PASSWORD", "admin123").getBytes()));
        log.debug("Setting admin password to '{}'", password);
        security.changePassword("admin", password);
        log.info("Admin password successfully set from Secret.");
      } else {
        log.info("Unable to retrieve secret 'nexus' from namespace");
      }
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