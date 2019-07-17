package com.redhat.labs.nexus.openshift;

import io.kubernetes.client.models.V1ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;

/**
 * A watcher for BlobStore configurations for Nexus. This watcher will NEVER DELETE BLOBSTORES, ONLY CREATE
 */

public class BlobStoreConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(BlobStoreConfigWatcher.class);

  private BlobStoreManager manager;

  public BlobStoreConfigWatcher(BlobStoreManager manager) {
    this.manager = manager;
  }

  public static void addBlobStore(V1ConfigMap configMap, BlobStoreManager manager) {
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