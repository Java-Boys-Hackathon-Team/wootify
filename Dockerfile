FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
RUN ./gradlew --no-daemon help > /dev/null
COPY . .
RUN ./gradlew --no-daemon -Pvaadin.productionMode=true bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /application
COPY --from=build /src/build/libs/*.jar wootify.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "wootify.jar"]
