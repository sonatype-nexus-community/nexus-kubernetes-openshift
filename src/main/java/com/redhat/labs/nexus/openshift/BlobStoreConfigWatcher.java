package com.redhat.labs.nexus.openshift;

/*-
 * #%L
 * com.redhat.labs.nexus:nexus-openshift-plugin
 * %%
 * Copyright (C) 2008 - 2019 Red Hat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.kubernetes.client.models.V1ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.BlobStoreApi;

/**
 * A watcher for BlobStore configurations for Nexus. This watcher will NEVER DELETE BLOBSTORES, ONLY CREATE
 */

public class BlobStoreConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(BlobStoreConfigWatcher.class);

  public void addBlobStore(V1ConfigMap configMap, BlobStoreApi manager) {
    String blobStoreName = configMap.getMetadata().getName();
    LOG.info("Provisioning BlobStore named '{}'", blobStoreName);
    // If the blobStoreName is defined and the blobstore does not already exist
    if (blobStoreName != null) {
      // A new ConfigMap labelled as a BlobStore config has been detected. Create the new BlobStore
      manager.createFileBlobStore(blobStoreName, String.format("/nexus-data/blobs/%s", blobStoreName));
    }
  }
}
