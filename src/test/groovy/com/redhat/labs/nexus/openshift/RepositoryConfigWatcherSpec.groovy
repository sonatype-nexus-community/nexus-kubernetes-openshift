package com.redhat.labs.nexus.openshift

import io.kubernetes.client.models.V1ConfigMap
import io.kubernetes.client.models.V1ObjectMeta
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.maven.LayoutPolicy
import org.sonatype.nexus.repository.maven.VersionPolicy
import org.sonatype.nexus.repository.storage.WritePolicy
import org.sonatype.nexus.script.plugin.RepositoryApi
import spock.lang.Specification

class RepositoryConfigWatcherSpec extends Specification {
  def "test create maven hosted repository using all defaults"() {
    given:
      def repositoryApi = Mock(RepositoryApi)
      def configMap = Mock(V1ConfigMap)
      def metadata = Mock(V1ObjectMeta)
      def data = [
          recipe: 'MavenHosted'
      ] as Map<String, String>
      def underTest = new RepositoryConfigWatcher()

    when:
      underTest.createNewRepository(repositoryApi, configMap)

    then:
      1 * configMap.getMetadata() >> metadata
      1 * metadata.getName() >> 'testRepository'
      6 * configMap.getData() >> data
      1 * repositoryApi.createMavenHosted("testRepository", BlobStoreManager.DEFAULT_BLOBSTORE_NAME, true, VersionPolicy.RELEASE, WritePolicy.ALLOW_ONCE, LayoutPolicy.STRICT)
  }

  def "test create docker hosted repository with httpPort 9080"() {
    given:
      def repositoryApi = Mock(RepositoryApi)
      def configMap = Mock(V1ConfigMap)
      def metadata = Mock(V1ObjectMeta)
      def data = [
              recipe: 'DockerHosted',
              httpPort: '9080'
      ] as Map<String, String>
      def underTest = new RepositoryConfigWatcher()

    when:
      underTest.createNewRepository(repositoryApi, configMap)

    then:
      1 * configMap.getMetadata() >> metadata
      1 * metadata.getName() >> "testRepository"
      8 * configMap.getData() >> data
      1 * repositoryApi.createDockerHosted("testRepository", 9080, null, BlobStoreManager.DEFAULT_BLOBSTORE_NAME, true, true, WritePolicy.ALLOW, true)
  }
}
