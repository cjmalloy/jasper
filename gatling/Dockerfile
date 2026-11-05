FROM maven:3.9.11-amazoncorretto-25-debian as builder
WORKDIR /gatling
COPY pom.xml .
RUN mvn -B clean package -Dmaven.main.skip -Dmaven.test.skip -Dcodegen.skip && rm -r target

FROM builder as test
WORKDIR /gatling
COPY src ./src/
COPY run.sh .
CMD ["./run.sh"]
