# Kubernetes/OpenShift Provisioning Plugin For [Sonatype Nexus](https://www.sonatype.com/nexus-repository-sonatype)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Build Status](https://travis-ci.com/InfoSec812/nexus-kubernetes-openshift.svg?branch=master)](https://travis-ci.com/InfoSec812/nexus-kubernetes-openshift)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=coverage)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=bugs)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=code_smells)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0&metric=security_rating)](https://sonarcloud.io/dashboard?id=com.redhat.labs.nexus%3Anexus-openshift-plugin%3A3.0)

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

## Configuration
For the most part, you should NOT need to do anything to configure this plugin. It will detect if it is
running inside of a Kubernetes/OpenShift cluster and default to using the service account settings
and environment variables defined in the Pod/Container. If you need to run this outside of 
a cluster but want to point at K8s API for configuration, you can assume the code will:

   *   If $KUBECONFIG is defined, use that config file.
   *   If $HOME/.kube/config can be found, use that.
   *   If the in-cluster service account can be found, assume in cluster config.
   *   Default to localhost:8080 as a last resort.

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
