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

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.util.Config;
import org.sonatype.goodies.lifecycle.LifecycleSupport;
import org.sonatype.nexus.BlobStoreApi;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.script.plugin.RepositoryApi;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.user.UserNotFoundException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;
import static org.sonatype.nexus.common.app.ManagedLifecycle.Phase.TASKS;

/**
 * Entrypoint for this plugin...
 */
@Named(OpenShiftConfigPlugin.TYPE)
@Singleton
@ManagedLifecycle(phase = TASKS)
public class OpenShiftConfigPlugin extends LifecycleSupport {
  static final String TYPE = "openshift-kubernetes-plugin";
  private static final String SERVICE_ACCOUNT_NAMESPACE_FILE = "/run/secrets/kubernetes.io/serviceaccount/namespace";

  // ***************************************************************************
  // *** Fields and methods are left 'package-private' to facilitate testing ***
  // ***************************************************************************

  @Inject
  BlobStoreApi blobStoreApi;

  @Inject
  BlobStoreManager blobStoreManager;

  @Inject
  RepositoryApi repository;

  @Inject
  RepositoryManager repositoryManager;

  @Inject
  SecuritySystem security;

  BlobStoreConfigWatcher blobStoreConfigWatcher;

  RepositoryConfigWatcher repositoryConfigWatcher;

  ApiClient client;
  CoreV1Api api;
  String namespace;

  /**
   * Called by the Nexus LifecyleManager, this method initializes all required
   * clients and configuratios.
   * @throws Exception If there is an error initializing the Kubernetes client
   */
  @Override
  protected void doStart() throws Exception {
    // This supports both stock K8s AND OpenShift so we don't have to use one or the other.
    // If running in OpenShift or K8s, it will automatically detect the correct settings
    // and service account credentials from the /run/secrets/kubernetes.io/serviceaccount
    // directory
    repositoryConfigWatcher = new RepositoryConfigWatcher();
    blobStoreConfigWatcher = new BlobStoreConfigWatcher();
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

  /**
   * Checks to see if the Kubernetes client is configured, then calls methods for
   * remaining operations.
   * @throws Exception When there is an error from the Kubernetes API client
   */
  void configureFromCluster() throws Exception {
    try {
      client.getBasePath();
      log.info("OpenShift/Kubernetes client successfully configured");
      setAdminPassword();
      readAndConfigure();
    } catch (IllegalStateException ise) {
      log.warn("OpenShift/Kubernetes client could not be configured", ise);
      throw new Exception("Unable to configure K8s/OpenShift client", ise);
    }
  }

  /**
   * Reads ConfigMaps with particular labels from the Kubernetes API and uses those
   * configurations to provision {@link org.sonatype.nexus.blobstore.api.BlobStore} and
   * {@link org.sonatype.nexus.repository.Repository} instances.
   */
  void readAndConfigure() {
    try {
      api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==blobstore", null, null, null, Boolean.FALSE)
          .getItems()
          .stream()
          .filter(configMap -> blobStoreManager.get(configMap.getMetadata().getName()) == null) // Filter out existing BlobStores
          .forEach(configMap -> {
            log.info("Provisioning blobstore named '{}'", configMap.getMetadata().getName());
            blobStoreConfigWatcher.addBlobStore(configMap, blobStoreApi);
          });

      api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE)
          .getItems()
          .stream()
          .filter(configMap -> repositoryManager.get(configMap.getMetadata().getName()) == null) // Filter out existing repositories
          .sorted(this::repositorySorter)  // Sort Group recipes to last
          .forEach(configMap -> {
            log.info("Provisioning repository named '{}'", configMap.getMetadata().getName());
            try {
              repositoryConfigWatcher.createNewRepository(repository, configMap);
            } catch (Exception e) {
              log.warn("Failed to create repository", e);
            }
          });
    } catch (ApiException e) {
      log.error("Error reading ConfigMaps", e);
    }
  }

  /**
   * Uses the Kubernetes API to find a {@link V1Secret} named 'nexus' and uses the password
   * field to set the admin password for Nexus. If no secret is found, it will:
   *
   * - Check for an environment variable `NEXUS_PASSWORD`
   *
   * - Default to "admin123"
   */
  void setAdminPassword() {
    try {
      V1Secret nexusSecret = api.readNamespacedSecret("nexus", namespace, null, null, null);
      if (nexusSecret != null) {
        Map<String, byte[]> secretData = nexusSecret.getData();
        secretData.keySet().forEach(s -> log.debug("{}:{}", s, new String(secretData.get(s))));
        String password = new String(secretData.getOrDefault("password", System.getenv().getOrDefault("NEXUS_PASSWORD", "admin123").getBytes()));
        security.changePassword("admin", password);
        log.info("Admin password successfully set from Secret.");
      } else {
        log.info("Unable to retrieve secret 'nexus' from namespace '{}'", namespace);
      }
    } catch (UserNotFoundException unfe) {
      log.warn("User 'admin' not found, unable to set password", unfe);
    } catch (Exception e) {
      log.warn("An error occurred while retrieving Secrets from OpenShift", e);
    }
  }

  /**
   * Sort list of repository ConfigMap objects such that all Group recipes will happen last
   * @param i1 The first {@link V1ConfigMap} object to be compared
   * @param i2 The second {@link V1ConfigMap} object to be compared
   * @return And integer indicating the order of the items in the list
   */
  int repositorySorter(V1ConfigMap i1, V1ConfigMap i2) {
    String recipeI1 = i1.getData().get("recipe");
    String recipeI2 = i2.getData().get("recipe");
    boolean i1IsGroup = recipeI1.endsWith("Group");
    boolean i2IsGroup = recipeI2.endsWith("Group");

    if (i1IsGroup && i2IsGroup) {
      return 0;
    } else if (!i2IsGroup) {
      return 1;
    } else {
      return -1;
    }
  }
}