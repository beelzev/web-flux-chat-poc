FROM gradle:jdk25 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jdk
WORKDIR /app
RUN mkdir -p /data
ENV SPRING_R2DBC_URL=r2dbc:h2:file:///data/chatdb;DB_CLOSE_DELAY=-1
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
