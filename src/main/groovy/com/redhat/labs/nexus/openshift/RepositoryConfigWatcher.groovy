package com.redhat.labs.nexus.openshift

import groovy.transform.PackageScope

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

import io.kubernetes.client.models.V1ConfigMap
import org.apache.commons.collections.list.FixedSizeList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.common.app.ApplicationVersion
import org.sonatype.nexus.common.app.VersionComparator

import javax.inject.Inject
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.maven.LayoutPolicy
import org.sonatype.nexus.repository.maven.VersionPolicy
import org.sonatype.nexus.repository.storage.WritePolicy
import org.sonatype.nexus.script.plugin.RepositoryApi
import org.sonatype.nexus.script.plugin.internal.provisioning.RepositoryApiImpl

import java.util.stream.Collectors

@Named
class RepositoryConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryConfigWatcher.class)

  @Inject
  @PackageScope
  RepositoryApi repositoryApi

  @Inject
  @PackageScope
  RepositoryManager repositoryManager

  @Inject
  ApplicationVersion applicationVersion

  VersionComparator versionComparator = new VersionComparator()

  // *** !!!WARNING!!! ***
  // The order of the fields in this map matches the order they must be in when passing them
  // as parameters to the appropriate method in RepositoryApi. Do not re-order/add/remove
  // fields without serious consideration.
  private final def VALID_RECIPES = [
          'AptHosted'     : [
                  distribution                : [type: 'String', required: false],
                  pgpPrivateKey               : [type: 'String', required: false],
                  pgpPassPhrase               : [type: 'String', required: false],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'AptProxy'      : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  distribution                : [type: 'String', required: false],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'BowerGroup'    : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'BowerHosted'   : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'BowerProxy'    : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  rewritePackageUrls          : [type: 'boolean', required: true, default: true]
          ],
          'DockerGroup'   : [
                  httpPort                    : [type: 'Integer', required: false],
                  httpsPort                   : [type: 'Integer', required: false],
                  members                     : [type: 'List<String>', required: true, default: ''],
                  v1Enabled                   : [type: 'boolean', required: true, default: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  forceBasicAuth              : [type: 'boolean', required: true, default: true]
          ],
          'DockerHosted'  : [
                  httpPort                    : [type: 'Integer', required: false],
                  httpsPort                   : [type: 'Integer', required: false],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  v1Enabled                   : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  forceBasicAuth              : [type: 'boolean', required: true, default: true, version: '3.14']
          ],
          'DockerProxy'   : [
                  remoteUrl                   : [type: 'String', required: true],
                  indexType                   : [type: 'String', required: true, default: 'REGISTRY'],
                  indexUrl                    : [type: 'String', required: false, default: 'https://hub.docker.io/'],
                  httpPort                    : [type: 'Integer', required: false],
                  httpsPort                   : [type: 'Integer', required: false],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  v1Enabled                   : [type: 'boolean', required: true, default: true]
          ],
          'GitLfsHosted'  : [
                  blobStoreName               : [type: 'String'],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'GolangGroup'   : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'GolangHosted'  : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'GolangProxy'   : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'MavenGroup'    : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'MavenHosted'   : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  versionPolicy               : [type: 'VersionPolicy', required: true, default: VersionPolicy.RELEASE],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW_ONCE],
                  layoutPolicy                : [type: 'LayoutPolicy', required: true, default: LayoutPolicy.STRICT]
          ],
          'MavenProxy'    : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  versionPolicy               : [type: 'VersionPolicy', required: true, default: VersionPolicy.RELEASE],
                  layoutPolicy                : [type: 'LayoutPolicy', required: true, default: LayoutPolicy.STRICT]
          ],
          'NpmGroup'      : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NpmHosted'     : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'NpmProxy'      : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'NugetGroup'    : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'NugetHosted'   : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'NugetProxy'    : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'PyPiGroup'     : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'PyPiHosted'    : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'PyPiProxy'     : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'RawGroup'      : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RawHosted'     : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'RawProxy'      : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'RubygemsGroup' : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'RubygemsHosted': [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW]
          ],
          'RubygemsProxy' : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ],
          'YumGroup'      : [
                  members                     : [type: 'List<String>', required: true, default: ''],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME]
          ],
          'YumHosted'     : [
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true],
                  writePolicy                 : [type: 'WritePolicy', required: true, default: WritePolicy.ALLOW],
                  depth                       : [type: 'Integer', required: true, default: 0]
          ],
          'YumProxy'      : [
                  remoteUrl                   : [type: 'String', required: true],
                  blobStoreName               : [type: 'String', required: true, default: BlobStoreManager.DEFAULT_BLOBSTORE_NAME],
                  strictContentTypeValidation : [type: 'boolean', required: true, default: true]
          ]
  ]

  /**
   * Given an existing Group repository name, update the member list
   * @param repository An instance of {@link RepositoryApi}
   * @param configMap A {@link V1ConfigMap} containing information about the Group
   */
  void updateGroupMembers(V1ConfigMap configMap) {
    def repo = repositoryManager.get(configMap.metadata.name)
    repo.configuration.attributes.group.memberNames = configMap.getData().get("members").split(',').toList().unique()
    repositoryManager.update(repo.configuration)
  }

  /**
   * Given a {@link V1ConfigMap} and an instance of {@link RepositoryApi}, parse the configmap
   * to determine the correct resipe and then use the defined recipe fields to call the
   * appropriate method on {@link RepositoryApi} with the appropriate parameters.
   * @param repository An instance of {@link RepositoryApi} used to create repository configurations
   * @param configMap A {@link V1ConfigMap} which SHOULD have the correct parameters for the indicated recipe
   * @throws Exception If there is an error provisioning the repository
   */
  void createNewRepository(V1ConfigMap configMap) throws Exception {
    String repositoryName = configMap.metadata.name
    LOG.info("Provisioning repository with name '{}'", repositoryName)
    def recipe = configMap.data.get("recipe") as String
    if (VALID_RECIPES.get(recipe) != null) {
      def fields = VALID_RECIPES[recipe]

      def fieldCount = fields.entrySet().stream().filter(this.&filterVersionedFields).count()

      // The list of parameters MUST exactly match the required number of parameters for
      // the specified recipe or the dynamic method call later will not work.
      def numberOfArguments = fieldCount + 1
      def parameters = FixedSizeList.decorate(Arrays.asList(new Object[numberOfArguments]))
      parameters.set(0, repositoryName)
      for (int x=1; x<numberOfArguments; x++) { parameters.set(x, null) }
      fields.entrySet().stream().filter(this.&filterVersionedFields).eachWithIndex { item, i ->
        // Since repository name is the first argument to ALL recipes, skip the first argument when doing assignments
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
      LOG.info('Method: {} - Parameters: {}', recipe, parameters)
      repositoryApi."create${recipe}"(*parameters)
    } else {
      LOG.warn("'${recipe}' is not a valid Nexus recipe")
    }
  }

  /**
   * For fields which are only available from a certain version of Nexus, filter out fields which are
   * not compatible with the running version of Nexus
   * @param fieldEntry An {@link Map.Entry} containing a Key and Value
   * @return {@code true} if the field is compatible with the current version of Nexus
   */
  @PackageScope
  boolean filterVersionedFields(Map.Entry<String, Map> fieldEntry) {
    if (fieldEntry.value?.version) {
      def fieldVersion = fieldEntry.value.version
      if (versionComparator.compare(fieldVersion, applicationVersion.version) <= 0) {
        return true
      } else {
        return false
      }
    } else {
      return true
    }
//    return (!fieldEntry.value.hasProperty('version') || versionComparator.compare(fieldEntry.value.version, applicationVersion.version) >= 0)
  }
}
