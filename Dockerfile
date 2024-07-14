FROM maven:3.9.7-eclipse-temurin-21 AS builder
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
COPY --from=oven/bun:1.1.17-alpine /usr/local/bin/bun /usr/local/bin/bun
ENV JASPER_NODE=/usr/local/bin/bun
CMD mvn -gs settings.xml test; \
		mkdir -p /tests && \
		cp target/surefire-reports/* /tests/

FROM azul/zulu-openjdk-debian:21.0.3-21.34-jre AS deploy
ENV BUN_RUNTIME_TRANSPILER_CACHE_PATH=0
ENV BUN_INSTALL_BIN=/usr/local/bin
COPY --from=oven/bun:1.1.18-slim /usr/local/bin/bun /usr/local/bin/
RUN ln -s /usr/local/bin/bun /usr/local/bin/bunx \
    && which bun \
    && which bunx \
    && bun --version
ARG JASPER_NODE=/usr/local/bin/bun
ENV JASPER_NODE=${JASPER_NODE}
RUN apt-get update && apt-get install python3 -y \
    && which python3 \
    && python3 --version
ARG JASPER_PYTHON=/usr/bin/python3
ENV JASPER_PYTHON=${JASPER_PYTHON}
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
