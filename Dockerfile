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
CMD mvn test

FROM azul/zulu-openjdk-alpine:17 as deploy
WORKDIR app
COPY --from=builder app/dependencies/ ./
COPY --from=builder app/spring-boot-loader/ ./
COPY --from=builder app/snapshot-dependencies/ ./
COPY --from=builder app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
