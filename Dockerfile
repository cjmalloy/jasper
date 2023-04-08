FROM maven:openjdk as builder
WORKDIR app
COPY pom.xml .
RUN mvn -B clean package -Dmaven.main.skip -Dmaven.test.skip -Dcodegen.skip && rm -r target
COPY src ./src
RUN mvn -B package -Dmaven.test.skip
# Check layers with
# java -Djarmode=layertools -jar target/docker-spring-boot-0.0.1.jar list
RUN java -Djarmode=layertools -jar target/*.jar extract

FROM builder as test
CMD mvn test; \
		mkdir -p /tests && \
		cp target/surefire-reports/* /tests/

FROM azul/zulu-openjdk-alpine:17 as deploy
WORKDIR app
COPY --from=builder app/dependencies/ ./
RUN true
COPY --from=builder app/spring-boot-loader/ ./
RUN true
COPY --from=builder app/snapshot-dependencies/ ./
RUN true
COPY --from=builder app/application/ ./
COPY docker/entrypoint.sh .
ENTRYPOINT sh entrypoint.sh
