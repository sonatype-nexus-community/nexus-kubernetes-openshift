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



  private static final String[] VALID_RECIPES = [
          'AptHosted': [
                  'name': 'String', 'description': 'String', 'pgpPrivateKey': 'String', 'pgpPassPhrase': 'String', 'blobStoreName': 'String', 'writePolicy': 'WritePolicy', 'strictContentTypeValidation': 'boolean',
                  defaults: [strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW, blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'AptProxy': [
                  'name': 'String', 'remoteUrl': 'String', 'blobStoreName': 'String', 'distribution': 'String', 'strictContentTypeValidation': 'boolean',
                  defaults: [strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'BowerGroup': [
                  'name': 'String', 'members': 'List<String>', 'blobStoreName': 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'BowerHosted': [
                  'name': 'String', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'writePolicy': 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'BowerProxy': [
                  'name': 'String', 'remoteUrl': 'String', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'rewritePackageUrls': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, rewritePackageUrls: true]
          ] as Map<String, Object>,
          'DockerGroup': [
                  'name': 'String', 'httpPort': 'Integer', 'httpsPort': 'Integer', 'members': 'List<String>', 'v1Enabled': 'boolean', 'blobStoreName': 'String', 'forceBasicAuth': 'boolean',
                  defaults: [v1Enabled: true, blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, forceBasicAuth: true]
          ] as Map<String, Object>,
          'DockerHosted': [
                  'name': 'String', 'httpPort': 'Integer', 'httpsPort': 'Integer', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'v1Enabled': 'boolean', 'writePolicy': 'WritePolicy', 'forceBasicAuth': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, v1Enabled: true, writePolicy: WritePolicy.ALLOW, forceBasicAuth: true]
          ] as Map<String, Object>,
          'DockerProxy': [
                  'name': 'String', 'remoteUrl': 'String', 'indexType': 'String', 'indexUrl': 'String', 'httpsPort': 'Integer', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'v1Enabled': 'boolean', 'forceBasicAuth': 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, v1Enabled: true, forceBasicAuth: true]
          ] as Map<String, Object>,
          'GitLfsHosted': [
                  'name': 'String', 'blobStoreName': 'String', 'strictContentTypeValidation': 'boolean', 'writePolicy': 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'GolangGroup': [
                  'name': 'String', 'members': 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'GolangHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'GolangProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'MavenGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'MavenHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', versionPolicy: 'VersionPolicy', writePolicy: 'WritePolicy', layoutPolicy: 'LayoutPolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, versionPolicy: VersionPolicy.RELEASE, writePolicy: WritePolicy.ALLOW_ONCE, layoutPolicy: LayoutPolicy.STRICT]
          ] as Map<String, Object>,
          'MavenProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', versionPolicy: 'VersionPolicy', layoutPolicy: 'LayoutPolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, versionPolicy: VersionPolicy.RELEASE, layoutPolicy: LayoutPolicy.STRICT]
          ] as Map<String, Object>,
          'NpmGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'NpmHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'NpmProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'NugetGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'NugetHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'NugetProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'PyPiGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'PyPiHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'PyPiProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'RawGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'RawHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                   defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'RawProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'RubygemsGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                   defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'RubygemsHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW]
          ] as Map<String, Object>,
          'RubygemsProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>,
          'YumGroup': [
                  'name': 'String', members: 'List<String>', blobStoreName: 'String',
                   defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ] as Map<String, Object>,
          'YumHosted': [
                  'name': 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean', writePolicy: 'WritePolicy', depth: 'Integer',
                   defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true, writePolicy: WritePolicy.ALLOW, depth: 0]
          ] as Map<String, Object>,
          'YumProxy': [
                  'name': 'String', remoteUrl: 'String', blobStoreName: 'String', strictContentTypeValidation: 'boolean',
                  defaults: [blobStoreName: BlobStoreManager.DEFAULT_BLOBSTORE_NAME, strictContentTypeValidation: true]
          ] as Map<String, Object>
  ] as Map<String, Map<String, Object>>

  private final RepositoryApi repository
  private BlobStoreManager blobStoreManager

  RepositoryConfigWatcher(RepositoryApi repository, BlobStoreManager blobStoreManager) {
    this.repository = repository
    this.blobStoreManager = blobStoreManager
  }

  void createNewRepository(RepositoryApi repository, V1ConfigMap configMap) throws Exception {
    String repositoryName = configMap.data.get("name")
    if (repositoryName != null) {
      def recipe = configMap.data.get("recipe") as String
      if (VALID_RECIPES.getAt(recipe) != null) {
        Map<String, Object> fields = VALID_RECIPES[recipe] as Map<String, Object>
        def parameters = [:]
        for (String key: fields.keySet()) {
          if (key != 'defaults') {
            String type = VALID_RECIPES[recipe][key]
            switch(type) {
              case 'String':
                parameters[key] = configMap.data.getOrDefault(key, (String)VALID_RECIPES[recipe]?.defaults?."${key}")
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'List<String>':
                parameters[key] = Arrays.asList(configMap.data.getOrDefault(key, (String)VALID_RECIPES[recipe]?.defaults?."${key}").split(','))
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'Integer':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}")
                try {
                  parameters[key] = Integer.parseInt(value)
                } catch (NumberFormatException nfe) {
                  LOG.error("Required parameter '{}' for recipe '{}' could not be parsed as an interger: {}" , recipe, key, value, nfe)
                }
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'boolean':
                parameters[key] = configMap.data.getOrDefault(key, (String)VALID_RECIPES[recipe]?.defaults?."${key}").toLowerCase().contentEquals("true")
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'WritePolicy':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase()
                parameters[key] = WritePolicy.valueOf(value)
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'VersionPolicy':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase()
                parameters[key] = VersionPolicy.valueOf(value)
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              case 'LayoutPolicy':
                def value = configMap.data.getOrDefault(key, (String) VALID_RECIPES[recipe]?.defaults?."${key}").toUpperCase()
                parameters[key] = LayoutPolicy.valueOf(value)
                if (parameters[key] == null) {
                  throw new Exception("Required parameter '${key}' for recipe '${recipe}' is null. Refusing to provision repository")
                }
                break
              default:
                LOG.warn("Unexpected field type '{}'", type)
            }
          }
        }
        repository."create${recipe}"(parameters)
      } else {
        LOG.warn("${recipe} is not a valid Nexus recipe")
      }
    } else {
      LOG.warn("Repository name is not set or repository already exists, refusing to recreate existing repository or unnamed repository")
    }
  }
}
