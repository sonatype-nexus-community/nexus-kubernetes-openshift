# Kubernetes/OpenShift Provisioning Plugin For [Sonatype Nexus](https://www.sonatype.com/nexus-repository-sonatype)

## Purpose
* Allow for BlobStores to be configured using ConfigMap objects labelled `nexus-type==blobstore`
* Allow for Repositories to be configured using ConfigMap objects labelled `nexus-type==repository`
* Allow for Admin password to be configured using Secret object named `nexus`