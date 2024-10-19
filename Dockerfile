FROM maven:3.9.8-amazoncorretto-21-debian AS builder
WORKDIR /app
COPY pom.xml .
COPY .m2/settings.xml .
RUN mvn -gs settings.xml -B clean package -Dmaven.main.skip -Dmaven.test.skip -Dcodegen.skip && rm -r target
COPY src ./src
RUN mvn -gs settings.xml -B package -Dmaven.test.skip
# Check layers with
# java -Djarmode=layertools -jar target/docker-spring-boot-0.0.1.jar list
RUN java -Djarmode=layertools -jar target/*.jar extract

FROM builder AS test
COPY docker/entrypoint.sh .
ENV BUN_RUNTIME_TRANSPILER_CACHE_PATH=0
ENV BUN_INSTALL_BIN=/usr/local/bin
COPY --from=oven/bun:1.1.31-slim /usr/local/bin/bun /usr/local/bin/
RUN ln -s /usr/local/bin/bun /usr/local/bin/bunx \
    && which bun \
    && which bunx \
    && bun --version
ENV JASPER_NODE=/usr/local/bin/bun
RUN apt-get update && apt-get install python3 python3-venv python3-pip python3-yaml -y \
    && which python3 \
    && python3 --version
ENV JASPER_PYTHON=/usr/bin/python3
RUN apt-get update && apt-get install wget bash jq uuid-runtime -y \
    && which jq \
    && jq --version \
    && uuidgen jq \
    && uuidgen --version \
    && which bash \
    && bash --version
ARG JASPER_SHELL=/usr/bin/bash
CMD mvn -gs settings.xml test; \
		mkdir -p /tests && \
		cp target/surefire-reports/* /tests/

FROM azul/zulu-openjdk-debian:21.0.5-21.38-jre AS deploy
ENV BUN_RUNTIME_TRANSPILER_CACHE_PATH=0
ENV BUN_INSTALL_BIN=/usr/local/bin
COPY --from=oven/bun:1.1.31-slim /usr/local/bin/bun /usr/local/bin/
RUN ln -s /usr/local/bin/bun /usr/local/bin/bunx \
    && which bun \
    && which bunx \
    && bun --version
ARG JASPER_NODE=/usr/local/bin/bun
ENV JASPER_NODE=${JASPER_NODE}
RUN apt-get update && apt-get install python3 python3-venv python3-pip python3-yaml -y \
    && which python3 \
    && python3 --version
ARG JASPER_PYTHON=/usr/bin/python3
ENV JASPER_PYTHON=${JASPER_PYTHON}
RUN apt-get update && apt-get install wget bash jq uuid-runtime -y \
    && which jq \
    && jq --version \
    && uuidgen jq \
    && uuidgen --version \
    && which bash \
    && bash --version
ARG JASPER_SHELL=/usr/bin/bash
ENV JASPER_SHELL=${JASPER_SHELL}
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
RUN true
COPY --from=builder /app/spring-boot-loader/ ./
RUN true
COPY --from=builder /app/snapshot-dependencies/ ./
RUN true
COPY --from=builder /app/application/ ./
COPY docker/entrypoint.sh .
ENTRYPOINT ["sh", "entrypoint.sh"]
