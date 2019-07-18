package com.redhat.labs.nexus.openshift


import groovy.json.JsonSlurper
import io.kubernetes.client.models.V1ConfigMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.config.Configuration

class RepositoryConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfigWatcher.class)

  private final RepositoryApi repository
  private BlobStoreManager blobStoreManager

  RepositoryConfigWatcher(RepositoryApi repository, BlobStoreManager blobStoreManager) {
    this.repository = repository
    this.blobStoreManager = blobStoreManager
  }

  void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) {
    String repositoryName = configMap.data.get("name")
    if (repositoryName != null) {
      def configMapJson = configMap.data.get("config") as String
      JsonSlurper parser = new JsonSlurper();

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
}
