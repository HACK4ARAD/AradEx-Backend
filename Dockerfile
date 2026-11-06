# Build multi-stage: Gradle (imaginea oficială, nu are nevoie de wrapper) → JRE 21 slim.
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
# întâi fișierele de build, ca dependențele să rămână în cache între deploy-uri
COPY settings.gradle build.gradle gradle.properties ./
COPY api/build.gradle api/
COPY app/build.gradle app/
RUN gradle --no-daemon -q :api:dependencies :app:dependencies > /dev/null 2>&1 || true
COPY api/src api/src
COPY app/src app/src
RUN gradle --no-daemon -q :app:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/app/build/libs/app.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
