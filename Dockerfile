FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy Maven wrapper and project files needed for build
COPY pom.xml .
COPY mvnw .
COPY .mvn/ .mvn/
COPY src/ src/

# Build the JAR inside the container
RUN ./mvnw clean package -DskipTests

# Copy only the built JAR
COPY target/eventy-users-service-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]