package com.redhat.labs.nexus.openshift;

/*-
 * #%L
 * com.redhat.labs.nexus:nexus-openshift-plugin
 * %%
 * Copyright (C) 2008 - 2019 Red Hat
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.util.Config;
import org.sonatype.goodies.lifecycle.LifecycleSupport;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.user.UserNotFoundException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
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
  private static final String RECIPE = "recipe";
  private static final String GROUP = "Group";

  // ***************************************************************************
  // *** Fields and methods are left 'package-private' to facilitate testing ***
  // ***************************************************************************

  @Inject
  BlobStoreManager blobStoreManager;

  @Inject
  RepositoryManager repositoryManager;

  @Inject
  SecuritySystem security;

  @Inject
  BlobStoreConfigWatcher blobStoreConfigWatcher;

  @Inject
  RepositoryConfigWatcher repositoryConfigWatcher;

  ApiClient client;
  CoreV1Api api;
  String namespace;
  String adminPassFile;
  File passFile = new File("/nexus-data/admin.password");

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
      readGeneratedAdminPasswordIfPresent();
      log.info("OpenShift/Kubernetes client successfully configured");
      setAdminPassword();
      readAndConfigure();
    } catch (IllegalStateException ise) {
      log.warn("OpenShift/Kubernetes client could not be configured", ise);
      throw new Exception("Unable to configure K8s/OpenShift client", ise);
    }
  }

  /**
   * Read the `/nexus-data/admin.password` if present to use it to configure the
   * Nexus Admin password.
   */
  void readGeneratedAdminPasswordIfPresent() {
    try {
      if (passFile.exists() && passFile.canRead()) {
        adminPassFile = Files.readAllLines(passFile.toPath()).get(0).trim();
      }
    } catch (IOException e) {
      adminPassFile = "admin123";
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
          .filter(this::filterExistingBlobStores) // Filter out existing BlobStores
          .forEach(configMap -> {
            log.info("Provisioning blobstore named '{}'", configMap.getMetadata().getName());
            blobStoreConfigWatcher.addBlobStore(configMap);
          });

      List<V1ConfigMap> allRepos = api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE)
          .getItems();
      /*
       * 1. Retrieve a list of all repository ConfigMaps
       * 2. Filter out repositories which already exist. This plugin cannot modify repositories.
       * 3. Sort the list so that all Group repositories are last to be provisioned.
       *    This allows non-groups to be created and available before assigning them to groups.
       * 4. Iterate over remaining ConfigMaps and provision the repositories
       */
      allRepos.stream().filter(this::filterExistingRepositories) // Filter out existing repositories
          .sorted(this::sortGroupRepositoriesToLast)  // Sort Group recipes to last
          .forEachOrdered(configMap -> {
            log.info("Provisioning repository named '{}'", configMap.getMetadata().getName());
            try {
              repositoryConfigWatcher.createNewRepository(configMap);
            } catch (Exception e) {
              log.warn("Failed to create repository", e);
            }
          });
      allRepos.stream().filter(this::filterExistingGroupRepositories)
          .forEach(configMap -> repositoryConfigWatcher.updateGroupMembers(configMap));
    } catch (ApiException e) {
      log.error("Error reading ConfigMaps", e);
    }
  }

  boolean filterExistingGroupRepositories(V1ConfigMap configMap) {
    if (configMap.getData().get(RECIPE).endsWith(GROUP)) {
      Repository repo = repositoryManager.get(configMap.getMetadata().getName());
      return repo != null;
    }
    return false;
  }

  boolean filterExistingRepositories(V1ConfigMap configMap) {
    if (configMap.getData().get(RECIPE) == null) {
      // If the configMap does not have a recipe specified, fail gracefully.
      log.warn("ConfigMap named '{}' does not specify a repository recipe. Ignoring.", configMap.getMetadata().getName());
      return false;
    }
    Repository repo = repositoryManager.get(configMap.getMetadata().getName());
    // If the repository does not yet exist, allow it
    return repo == null;
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
        String password = new String(secretData.getOrDefault("password", adminPassFile.getBytes()));
        if (!password.contentEquals(adminPassFile)) {
          security.changePassword("admin", password);
          log.info("Admin password successfully set from Secret.");
          try {
            Files.write(passFile.toPath(), password.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
          } catch (IOException e) {
            log.warn("Unable to update admin password file /nexus-data/admin.password");
          }
          return;
        }
        log.info("Admin password is already current.");
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
  int sortGroupRepositoriesToLast(V1ConfigMap i1, V1ConfigMap i2) {
    String recipeI1 = i1.getData().get(RECIPE);
    String recipeI2 = i2.getData().get(RECIPE);
    boolean i1IsGroup = recipeI1.endsWith(GROUP);
    boolean i2IsGroup = recipeI2.endsWith(GROUP);

    if (i1IsGroup && i2IsGroup) {
      return 0;
    } else if (!i2IsGroup) {
      return 1;
    } else {
      return -1;
    }
  }

  boolean filterExistingBlobStores(V1ConfigMap configMap) {
    return blobStoreManager.get(configMap.getMetadata().getName()) == null;
  }
}
