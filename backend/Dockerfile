# syntax=docker/dockerfile:1

# Immagine gradle ufficiale con la stessa versione dichiarata in gradle/wrapper/gradle-wrapper.properties
# (9.5.1): il build non deve piu' scaricare la distribuzione dal wrapper, che era il passo piu'
# fragile (services.gradle.org redirige su release-assets.githubusercontent.com e bastava un
# fallimento di DNS dentro il sandbox di build per far cadere tutto).
FROM gradle:9.5.1-jdk21 AS build
WORKDIR /app
ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY . .

# cache mount su GRADLE_USER_HOME: la cache delle dipendenze sopravvive fra un build e l'altro,
# quindi Maven Central viene interrogato una volta sola invece che a ogni build.
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
