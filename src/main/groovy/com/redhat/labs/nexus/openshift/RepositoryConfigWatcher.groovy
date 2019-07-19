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
                  'description': 'String', 'pgpPrivateKey': 'String', 'pgpPassPhrase': 'String', 'blobStoreName': 'String', 'writePolicy': 'WritePolicy', 'strictContentTypeValidation': 'boolean',
                  defaults: [strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW, blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'AptProxy'      : [
                  'remoteUrl': 'String', 'blobStoreName': 'String', 'distribution': 'String', 'strictContentTypeValidation': 'boolean',
                  defaults: [strictContentTypeValidation: true]
          ],
          'BowerGroup'    : [
                  'members': 'List<String>', 'blobStoreName': 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'BowerHosted'   : [
                  'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'writePolicy': 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'BowerProxy'    : [
                  'remoteUrl': 'String', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'rewritePackageUrls': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, rewritePackageUrls: true]
          ],
          'DockerGroup'   : [
                  'httpPort': 'Integer', 'httpsPort': 'Integer', 'members': 'List<String>', 'v1Enabled': 'boolean', 'blobStoreName': 'String', 'forceBasicAuth': 'boolean',
                  defaults: [v1Enabled: true, blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, forceBasicAuth: true]
          ],
          'DockerHosted'  : [
                  'httpPort': 'Integer', 'httpsPort': 'Integer', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'v1Enabled': 'boolean', 'writePolicy': 'WritePolicy', 'forceBasicAuth': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, v1Enabled: true, writePolicy: WritePolicy.ALLOW, forceBasicAuth: true]
          ],
          'DockerProxy'   : [
                  'remoteUrl': 'String', 'indexType': 'String', 'indexUrl': 'String', 'httpsPort': 'Integer', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'v1Enabled': 'boolean', 'forceBasicAuth': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, v1Enabled: true, forceBasicAuth: true]
          ],
          'GitLfsHosted'  : [
                  'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'writePolicy': 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'GolangGroup'   : [
                  'members': 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'GolangHosted'  : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'GolangProxy'   : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'MavenGroup'    : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'MavenHosted'   : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', versionPolicy: 'VersionPolicy', writePolicy: 'WritePolicy', layoutPolicy: 'LayoutPolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, versionPolicy: VersionPolicy.RELEASE, writePolicy: WritePolicy.ALLOW_ONCE, layoutPolicy: LayoutPolicy.STRICT]
          ],
          'MavenProxy'    : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', versionPolicy: 'VersionPolicy', layoutPolicy: 'LayoutPolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, versionPolicy: VersionPolicy.RELEASE, layoutPolicy: LayoutPolicy.STRICT]
          ],
          'NpmGroup'      : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NpmHosted'     : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'NpmProxy'      : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'NugetGroup'    : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NugetHosted'   : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'NugetProxy'    : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'PyPiGroup'     : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'PyPiHosted'    : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'PyPiProxy'     : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'RawGroup'      : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RawHosted'     : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'RawProxy'      : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'RubygemsGroup' : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RubygemsHosted': [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ],
          'RubygemsProxy' : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ],
          'YumGroup'      : [
                  members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'YumHosted'     : [
                  blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy', depth: 'Integer',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW, depth: 0]
          ],
          'YumProxy'      : [
                  remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ]
  ]

  void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) throws Exception {
    String repositoryName = configMap.metadata.name
    if (repositoryName != null) {
      def recipe = configMap.data.get("recipe") as String
      if (VALID_RECIPES.get(recipe) != null) {
        Map<String, Object> fields = VALID_RECIPES[recipe] as Map<String, Object>
        def parameters = [repositoryName] as List<Object>
        for (String key : fields.keySet()) {
          if (key != 'defaults' && key != 'recipe') {
            String type = VALID_RECIPES[recipe][key]
            switch (type) {
              case 'String':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}")
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'List<String>':
                def value = Arrays.asList(configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").split(','))
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'Integer':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}")
                if (value == null && !key.startsWith('http')) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                } else if (value != null) {
                  parameters.add(new Integer(Integer.parseInt(value)))
                } else if (key.startsWith('http')) {
                  parameters.add(null)
                }
                break
              case 'boolean':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toLowerCase().contentEquals("true")
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'WritePolicy':
                def value = WritePolicy.valueOf(configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase())
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'VersionPolicy':
                def value = VersionPolicy.valueOf(configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase())
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'LayoutPolicy':
                def value = LayoutPolicy.valueOf(configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase())
                parameters.add(value)
                if (value == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
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
