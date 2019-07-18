# Kubernetes/OpenShift Provisioning Plugin For [Sonatype Nexus](https://www.sonatype.com/nexus-repository-sonatype)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)

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
          <id>default</id>
          <repositories>
            <repository>
              <id>nexus-public-grid</id>
              <name>nexus-public-grid</name>
              <url>https://repository.sonatype.org/content/groups/sonatype-public-grid/</url>
              <releases/>
            </repository>
            <repository>
              <id>central</id>
              <name>central</name>
              <url>https://repo.maven.apache.org/maven2/</url>
              <releases/>
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
mvn clean package bundle:bundle
```
The **BUNDLE** jar file will be output in the project's root directory.


## Installing
You can copy the bundle to `/opt/sonatype/nexus/deploy` on any running Nexus container, but it would not be persistent across restarts.
The simplest option is to build a new container image based on the sonatype/nexus3 container and add the bundle JAR file to the new image.
Here's an example Dockerfile:

```dockerfile
FROM sonatype/nexus3:latest

COPY /path/to/bundle/nexus-openshift-plugin-3.17.0-01.jar /opt/sonatype/nexus/deploy
```
