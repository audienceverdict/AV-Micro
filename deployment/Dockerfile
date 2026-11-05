FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
