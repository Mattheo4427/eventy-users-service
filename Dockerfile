# eventy-users-service/Dockerfile (Version corrigée et robuste)

# First stage: Build the JAR
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 1. Copie des dépendances (pom.xml) et des scripts (.mvn, mvnw)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# 2. CORRECTION ROBUSTE DES FINS DE LIGNE ET PERMISSIONS
# La commande 'sed -i 's/\r$//' file' supprime les caractères de retour chariot (\r)
# qui causent l'erreur %0D et l'échec d'exécution de script.
RUN sed -i 's/\r$//' ./mvnw \
    && sed -i 's/\r$//' ./.mvn/wrapper/maven-wrapper.properties \
    && chmod +x ./mvnw

# 3. COMPILATION
COPY src/ src/
RUN ./mvnw clean package -DskipTests

# Second stage: Run the JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/eventy-users-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]