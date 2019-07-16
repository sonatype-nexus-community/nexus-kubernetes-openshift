package com.redhat.labs.nexus.openshift

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.storage.WritePolicy

import groovy.transform.CompileStatic

import static com.google.common.base.Preconditions.checkArgument
import static com.google.common.base.Preconditions.checkNotNull


public enum LayoutPolicy
{
  /**
   * Only allow repository paths that are Maven 2 standard layout compliant.
   */
  STRICT,

  /**
   * Allow any repository paths.
   */
  PERMISSIVE
}

/**
 * Repository version policy.
 *
 * @since 3.0
 */
public enum VersionPolicy
{
  /**
   * Only release coordinates allowed.
   */
  RELEASE,

  /**
   * Only snapshot coordinate allowed.
   */
  SNAPSHOT,

  /**
   * Both kind of coordinates allowed.
   */
  MIXED
}

/**
 * @since 3.0
 */
@Named(RepositoryApi.TYPE)
@Singleton
@CompileStatic
class RepositoryApi {

  public static final String TYPE = 'repository-api'

  RepositoryManager repositoryManager

  BlobStoreManager blobStoreManager

  @Inject
  RepositoryApi(RepositoryManager repositoryManager, BlobStoreManager blobStoreManager) {
    this.repositoryManager = repositoryManager
    this.blobStoreManager = blobStoreManager
  }

  /**
   * Create a hosted configuration for the given recipeName.
   */
  Configuration createHosted(final String name,
                             final String recipeName,
                             final String blobStoreName = BlobStoreManager.DEFAULT_BLOBSTORE_NAME,
                             final WritePolicy writePolicy = WritePolicy.ALLOW,
                             final boolean strictContentTypeValidation = true)
  {
    checkNotNull(name)
    checkArgument(recipeName && recipeName.endsWith('-hosted'))

    new Configuration(
            repositoryName: name,
            recipeName: recipeName,
            online: true,
            attributes: [
                    storage: [
                            blobStoreName              : blobStoreName,
                            writePolicy                : writePolicy,
                            strictContentTypeValidation: strictContentTypeValidation
                    ] as Map
            ] as Map
    )
  }

  /**
   * Create a proxy configuration for the given recipeName.
   */
  Configuration createProxy(final String name,
                            final String recipeName,
                            final String remoteUrl,
                            final String blobStoreName = BlobStoreManager.DEFAULT_BLOBSTORE_NAME,
                            final boolean strictContentTypeValidation = true)
  {
    checkNotNull(name)
    checkArgument(recipeName && recipeName.endsWith('-proxy'))

    new Configuration(
            repositoryName: name,
            recipeName: recipeName,
            online: true,
            attributes: [
                    httpclient   : [
                            connection: [
                                    blocked  : false,
                                    autoBlock: true
                            ] as Map
                    ] as Map,
                    proxy: [
                            remoteUrl     : remoteUrl,
                            contentMaxAge : 1440,
                            metadataMaxAge: 1440
                    ] as Map,
                    negativeCache: [
                            enabled   : true,
                            timeToLive: 1440
                    ] as Map,
                    storage      : [
                            blobStoreName              : blobStoreName,
                            strictContentTypeValidation: strictContentTypeValidation
                    ] as Map
            ] as Map
    )
  }

  /**
   * Create a group configuration for the given recipeName.
   */
  Configuration createGroup(final String name,
                            final String recipeName,
                            final String blobStoreName = BlobStoreManager.DEFAULT_BLOBSTORE_NAME,
                            final String... members)
  {
    checkNotNull(name)
    checkArgument(recipeName && recipeName.endsWith('-group'))

    new Configuration(
            repositoryName: name,
            recipeName: recipeName,
            online: true,
            attributes: [
                    group: [
                            memberNames: members.toList().unique()
                    ] as Map,
                    storage: [
                            blobStoreName: blobStoreName
                    ] as Map
            ] as Map
    )
  }

  void addRepository(Configuration config) {
    repositoryManager.create(config)
  }

  void updateRepository(Configuration config) {
    repositoryManager.update(config)
  }
}