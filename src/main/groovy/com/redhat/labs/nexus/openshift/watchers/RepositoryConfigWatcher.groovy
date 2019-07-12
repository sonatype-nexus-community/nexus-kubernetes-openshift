package com.redhat.labs.nexus.openshift.watchers

import com.redhat.labs.nexus.openshift.helpers.RepositoryApi
import groovy.json.JsonSlurper
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.storage.WritePolicy

class RepositoryConfigWatcher implements Watcher<ConfigMap> {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfigWatcher.class)

  private final RepositoryApi repository
  private BlobStoreManager blobStoreManager

  RepositoryConfigWatcher(RepositoryApi repository, BlobStoreManager blobStoreManager) {
    this.repository = repository
    this.blobStoreManager = blobStoreManager
  }

  @Override
  void eventReceived(Action action, ConfigMap configMap) {
    if (action == Action.ADDED) {
      createNewRepository(repository, configMap)
    }
  }

  static void createNewRepository(RepositoryApi repository, ConfigMap configMap) {
    String repositoryName = configMap.data.get("name")
    if (repositoryName != null) {
      def configMapJson = configMap.data.get("config") as String
      JsonSlurper parser = new JsonSlurper();
      def config = parser.parse(configMapJson.getBytes())
      if (repository.getRepositoryManager().exists(repositoryName)) {
        // Repository exists, update it if possible
        def existingRepo = repository.getRepositoryManager().get(repositoryName)
        if (existingRepo.configuration.recipeName == config.recipeName) {
          // Compatible recipes, update existing repository
          existingRepo.attributes = config.attributes
          repository.updateRepository(existingRepo)
        }
      } else {
        // Repository is new, create it if possible
        Configuration repoConfig = new Configuration(
                repositoryName: repositoryName,
                recipeName: config.recipeName,
                online: true,
                attributes: config.attributes
        )
        repository.addRepository(repoConfig)
      }
    } else {
      LOG.warn("Repository name is not set or repository already exists, refusing to recreate existing repository or unnamed repository")
    }
  }

  @Override
  public void onClose(KubernetesClientException e) {
    LOG.warn("Repository ConfigMap watcher has disconnected", e)
  }
}
