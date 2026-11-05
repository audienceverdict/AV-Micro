FROM eclipse-temurin:21-jre
WORKDIR /app
COPY SocialMedia/target/social-sync-worker-1.0.0.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
