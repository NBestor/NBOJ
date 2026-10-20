ARG NODE_BASE_IMAGE=node:24-bookworm-slim
ARG MAVEN_BASE_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG JRE_BASE_IMAGE=eclipse-temurin:17-jre
ARG DOCKER_CLI_IMAGE=docker:29-cli

FROM ${NODE_BASE_IMAGE} AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY config/route-ownership.json /workspace/config/route-ownership.json
COPY frontend ./
RUN npm run build

FROM ${MAVEN_BASE_IMAGE} AS backend-build

WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src src
COPY --from=frontend-build /workspace/src/main/resources/static src/main/resources/static
RUN ./mvnw -Dmaven.test.skip=true -Dskip.frontend=true package

FROM ${DOCKER_CLI_IMAGE} AS docker-cli

FROM ${JRE_BASE_IMAGE}

WORKDIR /app
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=backend-build /workspace/target/nboj-1.0.0.jar /app/app.jar
RUN mkdir -p /app/data /app/logs /tmp/wenzhong-docker-judge

ENV SERVER_PORT=8081
ENV EXECUTOR_MODE=docker
ENV OJ_CPP17_DOCKER_IMAGE=wenzhong-oj-cpp17-runner:13

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
