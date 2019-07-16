package com.redhat.labs.nexus.openshift.internal;

import com.google.common.reflect.TypeToken;
import com.redhat.labs.nexus.openshift.RepositoryApi;
import com.redhat.labs.nexus.openshift.RepositoryConfigWatcher;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1SecretList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.user.UserNotFoundException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Named(OpenShiftConfigPluginImpl.TYPE)
@Singleton
public class OpenShiftConfigPluginImpl {
  static final String TYPE = "openshift-kubernetes-plugin";

  private String namespace = null;

  private static final Logger LOG = LoggerFactory.getLogger(OpenShiftConfigPluginImpl.class);
  public static final String K8S_NAMESPACE = "/run/secrets/io.kubernetes/serviceaccount/namespace";

  private final BlobStoreManager blobStoreManager;

  private final RepositoryApi repository;

  private final SecuritySystem security;

  @Inject
  public OpenShiftConfigPluginImpl(
      BlobStoreManager blobStoreManager,
      RepositoryApi repository,
      SecuritySystem security) throws Exception {
    LOG.info("OpenShift/Kubernetes Plugin loading");
    this.blobStoreManager = blobStoreManager;
    this.repository = repository;
    this.security = security;
    LOG.info("OpenShift Plugin No-Args Constructor");
    // This supports both stock K8s AND OpenShift so we don't have to use one or the other.
    // If running in OpenShift or K8s, it will automatically detect the correct settings and service account credentials
    // from the /run/secrets/kubernetes.io/serviceaccount directory
    try {
      ApiClient client = Config.defaultClient();
      configureFromCluster(client);
    } catch (IOException ioe) {
      LOG.warn("Unable to read Kubernetes config");
    }
  }

  void configureFromCluster(ApiClient client) throws UserNotFoundException {
    namespace = System.getenv("KUBERNETES_NAMESPACE");
    if (namespace == null) {
      File namespaceFile = new File(K8S_NAMESPACE);
      if (namespaceFile.exists() && namespaceFile.canRead()) {
        try {
          namespace = Files.readAllLines(namespaceFile.toPath()).get(0).trim();
          if (namespace.length() > 0) {
            setAdminPassword(client);
            readAndConfigure(client, new BlobStoreConfigWatcherImpl(), new RepositoryConfigWatcher(repository, blobStoreManager));
          } else {
            LOG.warn("Namespace from /run/secrets/io.kubernetes/namespace is 0 bytes long.");
          }
        } catch (IOException ioe) {
          LOG.warn("Unable to read namespace from /run/secrets/io.kubernetes/namespace");
        }
      }
    } else {
      setAdminPassword(client);
    }
  }

  void readAndConfigure(ApiClient client, BlobStoreConfigWatcherImpl blobStoreConfigWatcher, RepositoryConfigWatcher repositoryConfigWatcher) {
    CoreV1Api api = new CoreV1Api(client);
    try {
      V1ConfigMapList blobStoreConfigMapList = api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type=blobstore", null, null, null, Boolean.FALSE);
      blobStoreConfigMapList.getItems().stream().forEach(configMap -> blobStoreConfigWatcher.addBlobStore(configMap, blobStoreManager));

      V1ConfigMapList repositoryConfigMapList = api.listNamespacedConfigMap(namespace, null, null, null, null, "nexus-type=repository", null, null, null, Boolean.FALSE);
      repositoryConfigMapList.getItems().stream().forEach(configMap -> repositoryConfigWatcher.createNewRepository(repository, configMap));
    } catch (ApiException e) {
      LOG.error("Unable to read configmaps from K8s API", e);
    }
  }

  void configureWatchers(ApiClient client, BlobStoreConfigWatcherImpl blobStoreConfigWatcher, RepositoryConfigWatcher repositoryConfigWatcher) {
    client.getHttpClient().setReadTimeout(0, TimeUnit.SECONDS);
    CoreV1Api api = new CoreV1Api(client);
    try {
      Watch<V1ConfigMap> blobStoreConfigWatch = Watch.createWatch(
          client, api.listNamespacedConfigMapCall(namespace, null, null, null, null, "nexus-type=blobstore", null, null, null, Boolean.TRUE, null, null), new TypeToken<Watch.Response<V1ConfigMap>>() {
          }.getType()
      );
      Consumer<V1ConfigMap> blobStoreHandler = configMap -> blobStoreConfigWatcher.addBlobStore(configMap, blobStoreManager);
      WatcherThread blobStoreWatcherThread = new WatcherThread(blobStoreConfigWatch, blobStoreHandler);
      ForkJoinPool.commonPool().execute(blobStoreWatcherThread);

      Watch<V1ConfigMap> repositoryConfigWatch = Watch.createWatch(
          client, api.listNamespacedConfigMapCall(namespace, null, null, null, null, "nexus-type=repository", null, null, null, Boolean.TRUE, null, null), new TypeToken<Watch.Response<V1ConfigMap>>() {
          }.getType()
      );
      Consumer<V1ConfigMap> repoHandler = configMap -> repositoryConfigWatcher.createNewRepository(repository, configMap);
      WatcherThread repositoryWatcherThread = new WatcherThread(repositoryConfigWatch, repoHandler);
      ForkJoinPool.commonPool().execute(repositoryWatcherThread);
    } catch (ApiException e) {
      LOG.error("Unable to watch configmaps from K8s API", e);
    }
//    client.configMaps().withLabel("nexus-type==blobstore").watch(blobStoreConfigWatcher);
//
//    client.configMaps().withLabel("nexus-type==repository").watch(repositoryConfigWatcher);
  }

  void setAdminPassword(ApiClient client) throws UserNotFoundException {
    CoreV1Api api = new CoreV1Api(client);
    V1SecretList secrets = null;
    try {
      secrets = api.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, Boolean.FALSE);
      Optional<V1Secret> nexusSecret = secrets.getItems().stream().filter(i -> i.getMetadata().getName().toLowerCase().contentEquals("nexus")).findFirst();
      if (nexusSecret.isPresent()) {
        security.changePassword("admin", new String(nexusSecret.get().getData().get("password")));
      }
    } catch (ApiException apie) {
      LOG.error("Unable to request secrets from K8s API", apie);
    }
  }
}
