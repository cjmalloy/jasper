FROM maven:3.9.11-amazoncorretto-25-debian
WORKDIR /gatling
COPY pom.xml .
COPY gen-index.sh .
COPY src ./src/
CMD mvn gatling:test; \
		mkdir -p /report && \
		cp -r target/gatling/* ./report/ && \
		./gen-index.sh
