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
package com.redhat.labs.nexus.openshift

import com.squareup.okhttp.Call
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Response
import com.squareup.okhttp.ResponseBody
import io.kubernetes.client.ApiClient
import io.kubernetes.client.JSON
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.models.V1ConfigMap
import io.kubernetes.client.models.V1ConfigMapList
import io.kubernetes.client.models.V1Secret
import io.kubernetes.client.util.Watch
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.security.SecuritySystem
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.SECONDS

class OpenShiftConfigPluginSpec extends Specification {

  def "Successfully sets admin password"() {
    given: "A Mock OpenShift client and a mock instance of the SecuritySystem object"
      def client = Mock(ApiClient)
      def api = Mock(CoreV1Api)
      def security = Mock(SecuritySystem)
      def blobStoreManager = Mock(BlobStoreManager)
      def repository = Mock(RepositoryApi)
      def V1Secret secret = Mock(V1Secret)
      def underTest = new OpenShiftConfigPlugin()
      underTest.blobStoreManager = blobStoreManager
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
      def mockItem = Mock(V1ConfigMap)
      def blobItemList = [mockItem] as List
      def repoItemList = [mockItem] as List
      def blobStoreManager = Mock(BlobStoreManager)
      def repository = Mock(RepositoryApi)
      def V1Secret secret = Mock(V1Secret)
      def underTest = new OpenShiftConfigPlugin()
      def mockRepoConfigWatcher = Mock(RepositoryConfigWatcher)
      def mockBlobStoreConfigWatcher = Mock(BlobStoreConfigWatcher)
      underTest.repositoryConfigWatcher = mockRepoConfigWatcher
      underTest.blobStoreConfigWatcher = mockBlobStoreConfigWatcher
      underTest.blobStoreManager = blobStoreManager
      underTest.repository = repository
      underTest.api = api
      underTest.namespace = "testnamespace"

    when:
      underTest.readAndConfigure()

    then:
      1 * api.listNamespacedConfigMap("testnamespace", null, null, null, null, "nexus-type==blobstore", null, null, null, Boolean.FALSE) >> blobStoreConfigMapList
      1 * api.listNamespacedConfigMap("testnamespace", null, null, null, null, "nexus-type==repository", null, null, null, Boolean.FALSE) >> repoStoreConfigMapList
      1 * blobStoreConfigMapList.getItems() >> blobItemList
      1 * repoStoreConfigMapList.getItems() >> repoItemList
      1 * mockBlobStoreConfigWatcher.addBlobStore(mockItem, blobStoreManager)
      1 * mockRepoConfigWatcher.createNewRepository(repository, mockItem)
  }

  def "Start watchers successfully"() {
    given:
      def client = Mock(ApiClient)
      def api = Mock(CoreV1Api)
      def blobStoreManager = Mock(BlobStoreManager)
      def repository = Mock(RepositoryApi)
      def httpClient = Mock(OkHttpClient)
      def underTest = Spy(OpenShiftConfigPlugin)
      def mockRepoConfigWatcher = Mock(RepositoryConfigWatcher)
      def mockBlobStoreConfigWatcher = Mock(BlobStoreConfigWatcher)
      underTest.repositoryConfigWatcher = mockRepoConfigWatcher
      underTest.blobStoreConfigWatcher = mockBlobStoreConfigWatcher
      underTest.blobStoreManager = blobStoreManager
      underTest.repository = repository
      underTest.api = api
      underTest.client = client
      underTest.namespace = "testnamespace"

    when:
      underTest.configureWatchers()

    then:
      1 * client.getHttpClient() >> httpClient
      1 * httpClient.setReadTimeout(0, SECONDS)
      2 * underTest.addWatcher(_, _) >> { return }
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
      1 * underTest.configureWatchers() >> { return }
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
}
