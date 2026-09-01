ARG RUNTIME_IMAGE=eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682
FROM ${RUNTIME_IMAGE}

RUN apk upgrade --no-cache

ARG OCI_CREATED
ARG OCI_REVISION
ARG OCI_SOURCE
ARG OCI_VERSION

LABEL org.opencontainers.image.created="${OCI_CREATED}" \
      org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.source="${OCI_SOURCE}" \
      org.opencontainers.image.version="${OCI_VERSION}"

WORKDIR /app
COPY --chown=10001:10001 build/libs/service.jar /app/service.jar

USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
