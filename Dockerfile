FROM openjdk:8-jdk-alpine

MAINTAINER Deltek TrafficLIVE Team "andreaghetti@deltek.com"

VOLUME /tmp
ADD ./target/MaconomyTrafficLiveBudgetIntegration*.jar MaconomyTrafficLiveBudgetIntegration.jar
ENTRYPOINT ["java", "-jar", "/MaconomyTrafficLiveBudgetIntegration.jar"]

EXPOSE 8888 8889
