# Kubernetes/OpenShift Provisioning Plugin For [Sonatype Nexus](https://www.sonatype.com/nexus-repository-sonatype)

## Purpose
* Allow for BlobStores to be configured using ConfigMap objects labelled `nexus-type==blobstore`
* Allow for Repositories to be configured using ConfigMap objects labelled `nexus-type==repository`
* Allow for Admin password to be configured using Secret object named `nexus`

## Requirements
* Java >= 1.8
* Maven >= 3.3
* Maven Settings configured to use [Sonatype Public Grid](https://repository.sonatype.org/content/groups/sonatype-public-grid/)
  ```xml
  <settings>
    <profiles>
      <profile>
        <id>puckboard</id>
        <repositories>
          <repository>
            <id>nexus-releases</id>
            <name>maven-releases</name>
            <url>https://nexus-rht-puckboard-ci-cd.apps.puckboard.rht-labs.com/repository/maven-releases/</url>
            <releases/>
          </repository>
          <repository>
            <id>nexus-snapshots</id>
            <name>maven-snapshots</name>
            <url>https://nexus-rht-puckboard-ci-cd.apps.puckboard.rht-labs.com/repository/maven-snapshots/</url>
            <snapshots/>
          </repository>
        </repositories>
      </profile>
    </profiles>
    
    <activeProfiles>
      <activeProfile>default</activeProfile>
    </activeProfiles>
  </settings>
  ```

## Building
```bash
mvn clean install -PbuildKar
```

## Installing?
Still trying to figure this out!