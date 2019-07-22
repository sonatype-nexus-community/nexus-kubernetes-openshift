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
package com.redhat.labs.nexus.openshift

import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.models.V1ConfigMap
import io.kubernetes.client.models.V1ConfigMapList
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1Secret
import org.sonatype.nexus.BlobStoreApi
import org.sonatype.nexus.blobstore.api.BlobStore
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.script.plugin.RepositoryApi
import org.sonatype.nexus.script.plugin.internal.provisioning.RepositoryApiImpl
import org.sonatype.nexus.security.SecuritySystem
import spock.lang.Specification

import java.util.stream.Collectors

class OpenShiftConfigPluginSpec extends Specification {

  def "Successfully sets admin password"() {
    given: "A Mock OpenShift client and a mock instance of the SecuritySystem object"
      def client = Mock(ApiClient)
      def api = Mock(CoreV1Api)
      def security = Mock(SecuritySystem)
      def blobStoreManager = Mock(BlobStoreApi)
      def repository = Mock(RepositoryApi)
      def secret = Mock(V1Secret)
      def underTest = new OpenShiftConfigPlugin()
      underTest.blobStoreApi = blobStoreManager
      underTest.repository = repository
      underTest.api = api
      underTest.client = client
      underTest.security = security
      underTest.namespace = "testnamespace"
      def data = ["password": "testpassword".getBytes()]

    when:
      underTest.setAdminPassword()

    then:
      1 * api.readNamespacedSecret("nexus", "testnamespace", null, null, null) >> secret
      1 * secret.getData() >> data
      1 * security.changePassword("admin", new String(data.password))
  }

  def "Successfully reads ConfigMaps and applies them"() {
    given:
      def api = Mock(CoreV1Api)
      def security = Mock(SecuritySystem)
      def blobStoreConfigMapList = Mock(V1ConfigMapList)
      def repoStoreConfigMapList = Mock(V1ConfigMapList)
      def mockItem = Mock(V1ConfigMap) {
        getData() >> [recipe: "DockerHosted"]
      }
      def mockMetaData = Mock(V1ObjectMeta)
      def blobItemList = [mockItem] as List
      def repoItemList = [mockItem] as List
      def blobStoreApi = Mock(BlobStoreApi)
      def blobStoreManager = Mock(BlobStoreManager)
      def repoManager = Mock(RepositoryManager)
      def repository = Mock(RepositoryApi)
      def V1Secret secret = Mock(V1Secret)
      def underTest = new OpenShiftConfigPlugin()
      def mockRepoConfigWatcher = Mock(RepositoryConfigWatcher)
      def mockBlobStoreConfigWatcher = Mock(BlobStoreConfigWatcher)
      underTest.repositoryConfigWatcher = mockRepoConfigWatcher
      underTest.blobStoreConfigWatcher = mockBlobStoreConfigWatcher
      underTest.blobStoreApi = blobStoreApi
      underTest.blobStoreManager = blobStoreManager
      underTest.repository = repository
      underTest.repositoryManager = repoManager
      underTest.api = api
      underTest.namespace = "testnamespace"

    when:
      underTest.readAndConfigure()

    then:
      1 * api.listNamespacedConfigMap("testnamespace", null, null, null, null, "nexus-type==blobstore", null, null, null, Boolean.FALSE) >> blobStoreConfigMapList
      1 * api.listNamespacedConfigMap("testnamespace", null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE) >> repoStoreConfigMapList
      1 * blobStoreConfigMapList.getItems() >> blobItemList
      1 * repoStoreConfigMapList.getItems() >> repoItemList
      1 * blobStoreManager.get(_) >> null
      1 * repoManager.get(_) >> null
      4 * mockItem.getMetadata() >> mockMetaData
      4 * mockMetaData.getName() >> "itemName"
      1 * mockBlobStoreConfigWatcher.addBlobStore(mockItem, blobStoreApi)
      1 * mockRepoConfigWatcher.createNewRepository(repository, mockItem)
  }

  def "configureFromCluster happy path"() {
    given:
      def client = Mock(ApiClient)
      def underTest = Spy(OpenShiftConfigPlugin)
      underTest.client = client

    when:
      underTest.configureFromCluster()

    then:
      1 * client.getBasePath() >> "BASE_PATH"
      1 * underTest.setAdminPassword() >> { return }
      1 * underTest.readAndConfigure() >> { return }
  }

  def "configureFromCluster sad path"() {
    given:
      def client = Mock(ApiClient)
      def underTest = Spy(OpenShiftConfigPlugin)
      underTest.client = client

    when:
      underTest.configureFromCluster()

    then:
      client.getBasePath() >> { throw Mock(IllegalStateException) }
      thrown(Exception)
  }

  def "test repositorySorting"() {
    given:
      def mockConfigMapProxy = Mock(V1ConfigMap) {
        getData() >> ["recipe": 'DockerProxy']
      }
      def mockConfigMapHosted = Mock(V1ConfigMap) {
        getData() >> ["recipe": 'DockerHosted']
      }
      def mockConfigMapGroup = Mock(V1ConfigMap) {
        getData() >> ["recipe": 'DockerGroup']
      }
      def configMapList = [mockConfigMapGroup, mockConfigMapGroup, mockConfigMapHosted, mockConfigMapHosted, mockConfigMapProxy, mockConfigMapGroup]
      def underTest = new OpenShiftConfigPlugin()

    when:
      def listResult = configMapList.stream().sorted(underTest.&sortGroupRepositoriesToLast).collect(Collectors.toList())

    then:
      listResult.get(3) == mockConfigMapGroup
      listResult.get(4) == mockConfigMapGroup
      listResult.get(5) == mockConfigMapGroup
  }

  def "Test existing group repositories filter predicate happy path"() {
    given:
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testRepository']
        getData() >> [recipe: 'MavenGroup']
      }
      def repositoryManager = Mock(RepositoryManager)
      def repositoryApi = Mock(RepositoryApiImpl) {
        getRepositoryManager() >> repositoryManager
      }
      def mockRepo = Mock(Repository)
      def underTest = new OpenShiftConfigPlugin()
      underTest.repositoryManager = repositoryManager
      underTest.repository = repositoryApi

    when:
      def result = underTest.filterExistingGroupRepositories(configMap)

    then:
      1 * repositoryManager.get('testRepository') >> mockRepo
      assert result
  }

  def "Test existing group repositories filter predicate sad path"() {
    given:
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testRepository']
        getData() >> [recipe: 'MavenGroup']
      }
      def repositoryManager = Mock(RepositoryManager)
      def repositoryApi = Mock(RepositoryApiImpl) {
        getRepositoryManager() >> repositoryManager
      }
      def underTest = new OpenShiftConfigPlugin()
      underTest.repositoryManager = repositoryManager
      underTest.repository = repositoryApi

    when:
      def result = underTest.filterExistingGroupRepositories(configMap)

    then:
      1 * repositoryManager.get('testRepository') >> null
      assert !result
  }

  def "Test filter existing repositories happy path"() {
    given:
      def repositoryManager = Mock(RepositoryManager)
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testRepository']
      }
      def underTest = new OpenShiftConfigPlugin()
      underTest.repositoryManager = repositoryManager

    when:
      def result = underTest.filterExistingRepositories(configMap)

    then:
      repositoryManager.get('testRepository') >> null
      assert result
  }

  def "Test filter existing repositories sad path"() {
    given:
      def repositoryManager = Mock(RepositoryManager)
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testRepository']
      }
      def mockRepo = Mock(Repository)
      def underTest = new OpenShiftConfigPlugin()
      underTest.repositoryManager = repositoryManager

    when:
      def result = underTest.filterExistingRepositories(configMap)

    then:
      repositoryManager.get('testRepository') >> mockRepo
      assert !result
  }

  def "Test filter existing blobstores"() {
    given:
      def blobStoreManager = Mock(BlobStoreManager)
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testBlobStore']
      }
      def mockBlobStore = Mock(BlobStore)
      def underTest = new OpenShiftConfigPlugin()
      underTest.blobStoreManager = blobStoreManager

    when:
      def result = underTest.filterExistingBlobStores(configMap)

    then:
      1 * blobStoreManager.get('testBlobStore') >> mockBlobStore
      assert !result
  }
}
