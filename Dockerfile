FROM eclipse-temurin:21-jre-alpine

WORKDIR /application

COPY build/libs/*.jar wootify.jar

ENTRYPOINT ["java","-jar","wootify.jar"]
