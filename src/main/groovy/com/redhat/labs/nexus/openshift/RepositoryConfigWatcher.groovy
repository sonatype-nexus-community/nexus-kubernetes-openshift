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

  private final Map<String, Object> VALID_RECIPES = [
          'AptHosted'     : [
                  description                : [type: 'String', required: false],
                  pgpPrivateKey              : [type: 'String', required: false],
                  pgpPassPhrase              : [type: 'String', required: false],
                  blobStoreName              : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  writePolicy                : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  strictContentTypeValidation: [type: 'boolean', required: true, default: Boolean.TRUE]
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
                  indexUrl                   : [type: 'String', required: false],
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

  void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) throws Exception {
    String repositoryName = configMap.metadata.name
    LOG.info("Provisioning repository with name '{}'", repositoryName)
    if (repositoryName != null) {
      def recipe = configMap.data.get("recipe") as String
      if (VALID_RECIPES.get(recipe) != null) {
        Map<String, Object> fields = VALID_RECIPES[recipe] as Map<String, Object>
        def parameters = [repositoryName] as List<Object>
        for (Map.Entry<String, Object> item : fields.entrySet()) {
          Map field = item.value
          String key = item.key
          if (key != 'indexUrl' && key != 'recipe') {
            String type = field.type
            switch (type) {
              case 'String':
                // indexType                  : [type: 'String', required: true, default: 'REGISTRY'],    // TODO: This will require special logic : REGISTRY, HUB, CUSTOM
                // indexUrl                   : [type: 'String', required: false],
                def value = configMap.data.getOrDefault(key, (String)field.default)
                parameters.add(value)

                // This handles a special case for DockerProxy recipe
                if (recipe == 'DockerProxy' && key == 'indexType' && value != 'REGISTRY') {
                  def indexUrlValue = configMap.data.getOrDefault('indexUrl', 'https://index.docker.io/')
                  parameters.add(indexUrlValue)
                } else if (recipe == 'DockerProxy' && key == 'indexType') { // If indexType is REGISTRY or HUB, set indexUrl to null
                  parameters.add(null)
                }
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'List<String>':
                def value = Arrays.asList(configMap.data.getOrDefault(key, (String)field.default).split(','))
                parameters.add(value)
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'Integer':
                def value = configMap.data.getOrDefault(key, (String) field.default)
                if (value == null && !key.startsWith('http') && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (value != null) {
                  parameters.add(new Integer(Integer.parseInt(value)))
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'boolean':
                def value = configMap.data.getOrDefault(key, (String) field.default).toLowerCase().contentEquals("true")
                parameters.add(value)
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'WritePolicy':
                def value = WritePolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
                parameters.add(value)
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'VersionPolicy':
                def value = VersionPolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
                parameters.add(value)
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              case 'LayoutPolicy':
                def value = LayoutPolicy.valueOf(configMap.data.getOrDefault(key, (String) field.default).toUpperCase())
                parameters.add(value)
                if (value == null && field.required) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (!field.required) {
                  parameters.add(null)
                }
                break
              default:
                LOG.warn("Unexpected field type '{}'", type)
            }
          }
        }
        repository."create${recipe}"(*parameters)
      } else {
        LOG.warn("${recipe} is not a valid Nexus recipe")
      }
    } else {
      LOG.warn("Repository name is not set or repository already exists, refusing to recreate existing repository or unnamed repository")
    }
  }
}
