FROM sonatype/nexus3:latest
LABEL maintainer="Deven Phillips <deven.phillips@redhat.com>" \
      vendor="Red Hat" \
      description="Sonatype Nexus repository manager with OpenShift Config plugin"
ARG NEXUS_VERSION=3.17.0-01

USER root
RUN sed -i 's@startLocalConsole=false@startLocalConsole=true@g' /opt/sonatype/nexus/bin/nexus.vmoptions \
    && chown nexus:nexus /opt -R \
    && chown nexus:nexus /nexus-data -R \
    && chmod 775 /opt -R \
    && chmod 775 /nexus-data -R
USER nexus