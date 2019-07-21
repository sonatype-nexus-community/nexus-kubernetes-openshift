package com.redhat.labs.nexus.openshift

import io.kubernetes.client.models.V1ConfigMap
import org.sonatype.nexus.BlobStoreApi
import spock.lang.Specification

class BlobStoreConfigWatcherSpec extends Specification {
  def "test addBlobStore"() {
    given:
      def configMap = Mock(V1ConfigMap) {
        getMetadata() >> [name: 'testBlobStore']
        getData() >> [type: 'File']
      }
      def blobStoreApi = Mock(BlobStoreApi)
      def underTest = new BlobStoreConfigWatcher()

    when:
      underTest.addBlobStore(configMap, blobStoreApi)

    then:
      1 * blobStoreApi.createFileBlobStore('testBlobStore', '/nexus-data/blobs/testBlobStore')
  }
}
