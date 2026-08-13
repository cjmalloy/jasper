FROM node:24.19.0-slim AS node

FROM maven:3.9.16-amazoncorretto-25-debian AS builder
WORKDIR /app
COPY pom.xml .
COPY .m2/settings.xml .
RUN mvn -gs settings.xml -B clean package -Dmaven.main.skip -Dmaven.test.skip -Dcodegen.skip && rm -r target
COPY src ./src
RUN mvn -gs settings.xml -B package -Dmaven.test.skip
# Check layers with
# java -Djarmode=tools -jar target/*.jar list-layers
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination layers

FROM builder AS test
COPY docker/entrypoint.sh .
COPY --from=node /usr/local/bin/node /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && which node \
    && node --version \
    && which npm \
    && npm --version
ENV JASPER_NODE=/usr/local/bin/node
ENV JASPER_NPM=/usr/local/bin/npm
RUN rm /etc/apt/sources.list.d/corretto.list
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
CMD mvn -gs settings.xml test jacoco:report surefire-report:report; \
		mkdir -p /tests && \
		cp target/surefire-reports/* /tests/ && \
		mkdir -p /reports && \
		cp -r target/reports/* /reports/ && \
		cp target/reports/surefire.html /reports/index.html && \
		mkdir -p /reports/coverage && \
		if [ -d target/site/jacoco ]; then cp -r target/site/jacoco/* /reports/coverage/; fi

FROM azul/zulu-openjdk-debian:25.0.3-25.34-jre AS deploy
RUN apt-get update && apt-get upgrade -y \
    && apt-get install curl -y \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=node /usr/local/bin/node /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && which node \
    && node --version \
    && which npm \
    && npm --version
ARG JASPER_NODE=/usr/local/bin/node
ENV JASPER_NODE=${JASPER_NODE}
ARG JASPER_NPM=/usr/local/bin/npm
ENV JASPER_NPM=${JASPER_NPM}
RUN apt-get update && apt-get install python3 python3-venv python3-pip python3-yaml -y \
    && which python3 \
    && python3 --version \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
ARG JASPER_PYTHON=/usr/bin/python3
ENV JASPER_PYTHON=${JASPER_PYTHON}
ENV PYTHONUNBUFFERED=1
RUN apt-get update && apt-get install wget bash jq uuid-runtime -y \
    && which jq \
    && jq --version \
    && uuidgen -r > /dev/null \
    && uuidgen --version \
    && which bash \
    && bash --version \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
ARG JASPER_SHELL=/usr/bin/bash
ENV JASPER_SHELL=${JASPER_SHELL}
RUN apt-get update && apt-get install -y \
    ffmpeg \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /var/lib/jasper
WORKDIR /app
COPY --from=builder /app/layers/dependencies/ ./
RUN true
COPY --from=builder /app/layers/spring-boot-loader/ ./
RUN true
COPY --from=builder /app/layers/snapshot-dependencies/ ./
RUN true
COPY --from=builder /app/layers/application/ ./
COPY docker/entrypoint.sh .
ENTRYPOINT ["sh", "entrypoint.sh"]
