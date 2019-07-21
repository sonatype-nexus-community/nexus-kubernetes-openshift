package com.redhat.labs.nexus.openshift

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

import io.kubernetes.client.models.V1ConfigMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.maven.LayoutPolicy
import org.sonatype.nexus.repository.maven.VersionPolicy
import org.sonatype.nexus.repository.storage.WritePolicy
import org.sonatype.nexus.script.plugin.RepositoryApi

class RepositoryConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfigWatcher.class)

  private final def VALID_RECIPES = [
          'AptHosted'     : [
                  description                : [type: 'String', required: false],
                  pgpPrivateKey              : [type: 'String', required: false],
                  pgpPassPhrase              : [type: 'String', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'AptProxy'      : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  distribution               : [type: 'String', required: false],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'BowerGroup'    : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'BowerHosted'   : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'BowerProxy'    : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  rewritePackageUrls         : [type: 'boolean', required: true, default: true]
          ],
          'DockerGroup'   : [
                  httpPort                    : [type: 'Integer', required: false],
                  httpsPort                   : [type: 'Integer', required: false],
                  members                     : [type: 'List<String>', required: false],
                  v1Enabled                   : [type: 'boolean', required: true, default: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  forceBasicAuth              : [type: 'boolean', required: true, default: true]
          ],
          'DockerHosted'  : [
                  httpPort                   : [type: 'Integer', required: false],
                  httpsPort                  : [type: 'Integer', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  v1Enabled                  : [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  forceBasicAuth             : [type: 'boolean', required: true, default: true]
          ],
          'DockerProxy'   : [
                  remoteUrl                  : [type: 'String', required: true],
                  indexType                  : [type: 'String', required: true, default: 'REGISTRY'],
                  indexUrl                   : [type: 'String', required: false, default: 'https://hub.docker.io/'],
                  httpPort                   : [type: 'Integer', required: false],
                  httpsPort                  : [type: 'Integer', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  v1Enabled                  : [type: 'boolean', required: true, default: true]
          ],
          'GitLfsHosted'  : [
                  blobStoreName              : [type: 'String'],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'GolangGroup'   : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'GolangHosted'  : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'GolangProxy'   : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'MavenGroup'    : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'MavenHosted'   : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  versionPolicy              : [type: 'VersionPolicy', required: true, default: VersionPolicy.RELEASE],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW_ONCE],
                  layoutPolicy               : [type: 'LayoutPolicy', required: true, default: LayoutPolicy.STRICT]
          ],
          'MavenProxy'    : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  versionPolicy              : [type: 'VersionPolicy', required: true, default: VersionPolicy.RELEASE],
                  layoutPolicy               : [type: 'LayoutPolicy', required: true, default: LayoutPolicy.STRICT]
          ],
          'NpmGroup'      : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NpmHosted'     : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'NpmProxy'      : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'NugetGroup'    : [
                  members      : [type: 'List<String>', required: false],
                  blobStoreName: [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NugetHosted'   : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'NugetProxy'    : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'PyPiGroup'     : [
                  members                     : [type: 'List<String>', required: false],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'PyPiHosted'    : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'PyPiProxy'     : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'RawGroup'      : [
                  members                     : [type: 'List<String>', required: false],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RawHosted'     : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'RawProxy'      : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'RubygemsGroup' : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RubygemsHosted': [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'RubygemsProxy' : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ],
          'YumGroup'      : [
                  members                    : [type: 'List<String>', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'YumHosted'     : [
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  depth                      : [type: 'Integer', required: true, default: 0]
          ],
          'YumProxy'      : [
                  remoteUrl                  : [type: 'String', required: true],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: true]
          ]
  ]

  /**
   * Given a {@link V1ConfigMap} and an instance of {@link RepositoryApi}, parse the configmap
   * to determine the correct resipe and then use the defined recipe fields to call the
   * appropriate method on {@link RepositoryApi} with the appropriate parameters.
   * @param repository An instance of {@link RepositoryApi} used to create repository configurations
   * @param configMap A {@link V1ConfigMap} which SHOULD have the correct parameters for the indicated recipe
   * @throws Exception If there is an error provisioning the repository
   */
  void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) throws Exception {
    String repositoryName = configMap.metadata.name
    LOG.info("Provisioning repository with name '{}'", repositoryName)
    def recipe = configMap.data.get("recipe") as String
    if (VALID_RECIPES.get(recipe) != null) {
      def fields = VALID_RECIPES[recipe]

      // The list of parameters MUST exactly match the required number of parameters for
      // the specified recipe or the dynamic method call later will not work.
      def parameters = [repositoryName] as List<Object>
      fields.each {
        parameters.add(null)
      }
      fields.entrySet().eachWithIndex { item, i ->
        def idx = i + 1
        def field = item.value
        String key = item.key
        String type = field.type
        switch (type) {
          case 'String':
            def value = configMap.data.getOrDefault(key, (String)field.default)
            parameters.set(idx, value)
            if (value == null && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            }
            break
          case 'List<String>':
            def value = Arrays.asList(configMap.data.getOrDefault(key, (String)field.default).split(','))
            parameters.set(idx, value)
            if (value == null && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            } else if (!field.required) {
              parameters.set(idx, null)
            }
            break
          case 'Integer':
            def value = configMap.data.getOrDefault(key, (String) field.default)
            if (value == null && !key.startsWith('http') && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            } else if (value != null) {
              parameters.set(idx, new Integer(Integer.parseInt(value)))
            } else if (!field.required) {
              parameters.set(idx, null)
            }
            break
          case 'boolean':  // All booleans in the config have a default value, so no need to do a null check on this branch
            def value = configMap.data.getOrDefault(key, (String) field.default).toLowerCase().contentEquals("true")
            parameters.set(idx, value)
            break
          case 'WritePolicy':
            def value = WritePolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
            parameters.set(idx, value)
            if (value == null && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            } else if (!field.required) {
              parameters.set(idx, null)
            }
            break
          case 'VersionPolicy':
            def value = VersionPolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
            parameters.set(idx, value)
            if (value == null && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            } else if (!field.required) {
              parameters.set(idx, null)
            }
            break
          case 'LayoutPolicy':
            def value = LayoutPolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
            parameters.set(idx, value)
            if (value == null && field.required) {
              throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
            } else if (!field.required) {
              parameters.set(idx, null)
            }
            break
          default:
            LOG.warn("Unexpected field type '{}'", type)
        }
      }
      repository."create${recipe}"(*parameters)
    } else {
      LOG.warn("'${recipe}' is not a valid Nexus recipe")
    }
  }
}
