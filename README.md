# Kubernetes/OpenShift Provisioning Plugin For [Sonatype Nexus](https://www.sonatype.com/nexus-repository-sonatype)

[![Build Status](https://travis-ci.com/InfoSec812/nexus-kubernetes-openshift.svg?branch=v0.2.4)](https://travis-ci.com/InfoSec812/nexus-kubernetes-openshift)
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

## Installing
You can copy the bundle to `/opt/sonatype/nexus/deploy` on any running Nexus container, but it would not be persistent across restarts.
The simplest option is to build a new container image based on the sonatype/nexus3 container and add the bundle JAR file to the new image.
Here's how to create a container image:

```bash
## The Dockefiles are set to install from the GitHub Releases, no compilation required
docker build -t nexus-kubernetes -f Dockerfile . 

docker build -t nexus-openshift -f Dockerfile.rhel7 .
```
*NOTE: To build the OpenShift image, you MUST have access to `registry.connect.redhat.com` and have Docker configured to authenticate to that registry.*

## Configuration
For the most part, you should NOT need to do anything to configure this plugin. It will detect if it is
running inside of a Kubernetes/OpenShift cluster and default to using the service account settings
and environment variables defined in the Pod/Container. If you need to run this outside of 
a cluster but want to point at K8s API for configuration, you can assume the code will:

   *   If $KUBECONFIG is defined, use that config file.
   *   If $HOME/.kube/config can be found, use that.
   *   If the in-cluster service account can be found, assume in cluster config.
   *   Default to localhost:8080 as a last resort.

## Setting Admin Password
The Admin password can be set using a `Secret` named `nexus` which contains a key called `password`. For example:

```yaml
apiVersion: v1
stringData:
  password: <MY SUPER SECRET PASSWORD>
kind: Secret
metadata:
  name: nexus
  namespace: labs-ci-cd
type: Opaque
```

## Provisioning BlobStores
Right now, this ONLY supports File blobstores. Perhaps later, S3 blobstores can be supported.

### Example ConfigMap
```yaml
apiVersion: v1
data:
  type: 'File'
kind: ConfigMap
metadata:
  name: my-blobstore
  namespace: labs-ci-cd
  labels:
    nexus-type: blobstore
```

## Provisioning Repositories
Nexus supports a number of different repository types, and each one has different required settings.

### Example ConfigMap
```yaml
apiVersion: v1
data:
  recipe: 'MavenProxy'
  remoteUrl: 'https://repo.maven.apache.org/maven2/'
  blobStoreName: 'default'
  strictContentTypeValidation: 'true'
  versionPolicy: 'RELEASE'
  layoutPolicy: 'STRICT'
kind: ConfigMap
metadata:
  name: maven-central
  namespace: labs-ci-cd
  labels:
    nexus-type: repository
```
More example ConfigMaps can be seen [HERE](src/test/resources/exampleConfigMaps/)

There are a few enumerated data types which you need to know as well:

<table>
  <thead>
    <tr>
        <th>Type</th>
        <th>Allowed Values</th>
    </tr>
  </thead>
  <tr>
    <td>WritePolicy</td>
    <td>ALLOW, ALLOW_ONCE, DENY</td>
  </tr>
  <tr>
    <td>VersionPolicy</td>
    <td>RELEASE, SNAPSHOT, MIXED</td>
  </tr>
  <tr>
    <td>LayoutPolicy</td>
    <td>STRICT, PERMISSIVE</td>
  </tr>
</table>

Listed below are the fields required for each repository type:

<table>
    <thead>
        <tr>
            <th>Repository Type</th>
            <th>Field</th>
            <th>Type</th>
            <th>Required</th>
            <th>Default Value</th>
        </tr>
    </thead>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/nxrm3master/formats/apt-repositories">AptHosted</a></td>
        <td>description</td>
        <td>String</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>pgpPrivateKey</td>
        <td>String</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>pgpPassPhrase</td>
        <td>String</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/nxrm3master/formats/apt-repositories">AptProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>distribution</td>
        <td>String</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/bower-repositories">BowerGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/bower-repositories">BowerHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/bower-repositories">BowerProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>rewritePackageUrls</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/docker-registry">DockerGroup</a></td>
        <td>httpPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>httpsPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>v1Enabled</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>forceBasicAuth</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/docker-registry">DockerHosted</a></td>
        <td>httpPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>httpsPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>v1Enabled</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td></td>
        <td>forceBasicAuth</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/docker-registry">DockerProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>indexType</td>
        <td>String</td>
        <td>true</td>
        <td>REGISTRY (Can be REGISTRY, HUB, CUSTOM; CUSTOM requires setting indexUrl below)</td>
    </tr>
    <tr>
        <td></td>
        <td>indexUrl</td>
        <td>String</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>httpPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>httpsPort</td>
        <td>Integer</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>v1Enabled</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/git-lfs-repositories">GitLfsHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>null</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/go-repositories">GolangGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/go-repositories">GolangHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/go-repositories">GolangProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/maven-repositories">MavenGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/maven-repositories">MavenHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>versionPolicy</td>
        <td>VersionPolicy</td>
        <td>true</td>
        <td>RELEASE</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW_ONCE</td>
    </tr>
    <tr>
        <td></td>
        <td>layoutPolicy</td>
        <td>LayoutPolicy</td>
        <td>true</td>
        <td>STRICT</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/maven-repositories">MavenProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>versionPolicy</td>
        <td>VersionPolicy</td>
        <td>true</td>
        <td>RELEASE</td>
    </tr>
    <tr>
        <td></td>
        <td>layoutPolicy</td>
        <td>LayoutPolicy</td>
        <td>true</td>
        <td>STRICT</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/npm-registry">NpmGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/npm-registry">NpmHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/npm-registry">NpmProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/nuget-repositories">NugetGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/nuget-repositories">NugetHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/nuget-repositories">NugetProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/pypi-repositories">PyPiGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/pypi-repositories">PyPiHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/pypi-repositories">PyPiProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/raw-repositories">RawGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/raw-repositories">RawHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/raw-repositories">RawProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/rubygems-repositories">RubygemsGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/rubygems-repositories">RubygemsHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/rubygems-repositories">RubygemsProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/yum-repositories">YumGroup</a></td>
        <td>members</td>
        <td>List&lt;String&gt; (comma-separated list of repositories)</td>
        <td>false</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/yum-repositories">YumHosted</a></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
    <tr>
        <td></td>
        <td>writePolicy</td>
        <td>WritePolicy</td>
        <td>true</td>
        <td>ALLOW</td>
    </tr>
    <tr>
        <td></td>
        <td>depth</td>
        <td>Integer</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td><a target="_blank" href="https://help.sonatype.com/repomanager3/formats/yum-repositories">YumProxy</a></td>
        <td>remoteUrl</td>
        <td>String</td>
        <td>true</td>
        <td>&lt;N/A&gt;</td>
    </tr>
    <tr>
        <td></td>
        <td>blobStoreName</td>
        <td>String</td>
        <td>true</td>
        <td>default</td>
    </tr>
    <tr>
        <td></td>
        <td>strictContentTypeValidation</td>
        <td>boolean</td>
        <td>true</td>
        <td>true</td>
    </tr>
</table>


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