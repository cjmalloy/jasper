FROM maven:3.9.9-amazoncorretto-21-debian
WORKDIR /gatling
COPY pom.xml .
COPY gen-index.sh .
COPY src ./src/
CMD mvn gatling:test; \
		mkdir -p /report && \
		cp -r target/gatling/* ./report/ && \
		./gen-index.sh
