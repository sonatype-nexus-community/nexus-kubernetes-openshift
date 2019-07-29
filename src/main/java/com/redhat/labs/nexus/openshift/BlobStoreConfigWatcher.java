package com.redhat.labs.nexus.openshift;

/*-
 * #%L
 * com.redhat.labs.nexus:nexus-openshift-plugin
 * %%
 * Copyright (C) 2008 - 2019 Red Hat
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import io.kubernetes.client.models.V1ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.BlobStoreApi;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * A watcher for BlobStore configurations for Nexus. This watcher will NEVER DELETE BLOBSTORES, ONLY CREATE
 */
@Named
@Singleton
public class BlobStoreConfigWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(BlobStoreConfigWatcher.class);

  @Inject
  BlobStoreApi blobStoreApi;

  public void addBlobStore(V1ConfigMap configMap) {
    String blobStoreName = configMap.getMetadata().getName();
    LOG.info("Provisioning BlobStore named '{}'", blobStoreName);
    // If the blobStoreName is defined and the blobstore does not already exist
    if (blobStoreName != null) {
      // A new ConfigMap labelled as a BlobStore config has been detected. Create the new BlobStore
      blobStoreApi.createFileBlobStore(blobStoreName, String.format("/nexus-data/blobs/%s", blobStoreName));
    }
  }
}
