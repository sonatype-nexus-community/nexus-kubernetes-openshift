package com.redhat.labs.nexus.openshift.internal;

import io.kubernetes.client.models.V1ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;

public class BlobStoreConfigWatcherImpl {

  private static final Logger LOG = LoggerFactory.getLogger(BlobStoreConfigWatcherImpl.class);

  public void addBlobStore(V1ConfigMap configMap, BlobStoreManager manager) {
    String blobStoreName = configMap.getData().get("name");
    // If the blobStoreName is defined and the blobstore does not already exist
    if (blobStoreName != null && !manager.exists(blobStoreName)) {
      // A new ConfigMap labelled as a BlobStore config has been detected. Create the new BlobStore
      BlobStoreConfiguration newConfig = new BlobStoreConfiguration();
      newConfig.setName(configMap.getData().get("name"));
      newConfig.setType(configMap.getData().getOrDefault("type", "File"));
      newConfig.setWritable(configMap.getData().getOrDefault("writable", "true").toLowerCase().startsWith("t"));

      // At a later date if other blobstore formats besides 'File' are desirable, we can implement the required attributes here

      try {
        manager.create(newConfig);
      } catch (Exception e) {
        LOG.error(String.format("Unable to create blobstore named '%s'", blobStoreName), e);
      }
    }
  }
}
