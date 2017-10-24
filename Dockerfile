FROM openjdk:8-jdk-alpine

MAINTAINER Deltek TrafficLIVE Team "andreaghetti@deltek.com"

VOLUME /tmp
ADD ./target/MaconomyTrafficLiveBudgetIntegration*.jar MaconomyTrafficLiveBudgetIntegration.jar
ENTRYPOINT ["java", "$JAVA_OPTS", "-jar", "/MaconomyTrafficLiveBudgetIntegration.jar"]

EXPOSE 8888 8889
