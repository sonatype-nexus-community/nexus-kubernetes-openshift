package com.redhat.labs.nexus.openshift


import spock.lang.Specification

class OpenShiftConfigPluginSpec extends Specification {

/*  def "Successfully sets admin password"() {
    given: "A Mock OpenShift client and a mock instance of the SecuritySystem object"
      def client = Mock(OpenShiftClient)
      def security = Mock(SecuritySystem)
      def blobStoreManager = Mock(BlobStoreManager)
      def repository = Mock(RepositoryApi)
      def secrets = Mock(MixedOperation)
      def nameable = Mock(SecretOperationsImpl)
      def secret = Mock(Secret)
      def underTest = Spy(OpenShiftConfigPlugin)

    when:
      underTest.setAdminPassword(client)

    then:
      1 * client.secrets() >> secrets
      1 * secrets.withName("nexus") >> nameable
      1 * secret.get() >> secret
      1 * secret.getData() >> [password: "testPassword"]
      1 * security.changePassword("admin", "testPassword")
  }*/
}
