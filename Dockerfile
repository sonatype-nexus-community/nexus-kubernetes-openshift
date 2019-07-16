FROM sonatype/nexus3:latest
LABEL maintainer="Deven Phillips <deven.phillips@redhat.com>" \
      vendor="Red Hat" \
      description="Sonatype Nexus repository manager with OpenShift Config plugin"
ARG NEXUS_VERSION=3.17.0-01

USER root
ENV OK_HTTP_PKG org/apache/servicemix/bundles/okhttp/2.7.5_1
ENV OK_HTTP_BUNDLE /opt/sonatype/nexus/system/${OK_HTTP_PKG}
ENV OK_HTTP_ARTIFACT org.apache.servicemix.bundles.okhttp-2.7.5_1.jar
ENV OKIO_ARTIFACT org.apache.servicemix.bundles.okio-1.6.0_1.jar
ENV OKIO_PKG org/apache/servicemix/bundles/org.apache.servicemix.bundles.okio/1.6.0_1
ENV OKIO_BUNDLE /opt/sonatype/nexus/system/${OKIO_PKG}
RUN mkdir -p ${OK_HTTP_BUNDLE} \
    && curl -o ${OK_HTTP_BUNDLE}/${OK_HTTP_ARTIFACT} https://repo1.maven.org/maven2/${OK_HTTP_PKG}/${OK_HTTP_ARTIFACT} \
    && mkdir -p ${OKIO_BUNDLE} \
    && curl -o ${OKIO_BUNDLE}/${OKIO_ARTIFACT} https://repo1.maven.org/maven2/${OKIO_PKG}/${OKIO_ARTIFACT}
RUN sed -i 's@startLocalConsole=false@startLocalConsole=true@g' /opt/sonatype/nexus/bin/nexus.vmoptions \
    && echo -n "-Dorg.ops4j.pax.url.mvn.repositories = http://repo1.maven.org/maven2@id=central" >> /opt/sonatype/nexus/bin/nexus.vmoptions \
    && echo -n ", http://repository.jboss.org/nexus/content/groups/public@id=jboss" >> /opt/sonatype/nexus/bin/nexus.vmoptions \
    && echo ", https://repository.sonatype.org/content/groups/sonatype-public-grid/@id=sonatype-public-grid" >> /opt/sonatype/nexus/bin/nexus.vmoptions \
    && chown nexus:nexus /opt -R \
    && chown nexus:nexus /nexus-data -R \
    && chmod 775 /opt -R \
    && chmod 775 /nexus-data -R
USER nexus