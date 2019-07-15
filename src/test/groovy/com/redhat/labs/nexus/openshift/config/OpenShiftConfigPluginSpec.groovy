package com.redhat.labs.nexus.openshift.config

import com.redhat.labs.nexus.openshift.helpers.RepositoryApi
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.base.BaseOperation
import io.fabric8.kubernetes.client.dsl.internal.SecretOperationsImpl
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.security.SecuritySystem
import spock.lang.Specification

class OpenShiftConfigPluginSpec extends Specification {

/*  def "Successfully sets admin password"() {
    given: "A Mock OpenShift client and a mock instance of the SecuritySystem object"
      def client = GroovyMock(DefaultOpenShiftClient, global: true)
      def security = Mock(SecuritySystem)
      def blobStoreManager = Mock(BlobStoreManager)
      def repository = Mock(RepositoryApi)
      def secrets = Mock(MixedOperation)
      def nameable = Mock(SecretOperationsImpl)
      def secret = Mock(Secret)
      def underTest = new OpenShiftConfigPlugin(blobStoreManager, repository, security)

    when:
      underTest.setAdminPassword(client)

    then:
      1 * new DefaultOpenShiftClient() >> client
      1 * client.secrets() >> secrets
      1 * secrets.withName("nexus") >> nameable
      1 * secret.get() >> secret
      1 * secret.getData() >> [password: "testPassword"]
      1 * security.changePassword("admin", "testPassword")
  }*/
}
