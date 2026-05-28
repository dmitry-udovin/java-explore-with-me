FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/*.jar service-main.jar
ENTRYPOINT ["java", "-jar", "service-main.jar"]
