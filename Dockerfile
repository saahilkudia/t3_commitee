# Use Temurin JDK 21 Alpine as the base
FROM eclipse-temurin:21-jdk-alpine

# NEW: Install mysql-client and bash[cite: 1]
# This provides the actual 'mysqldump' and 'mysql' files needed by the code.
RUN apk update && apk add --no-cache mysql-client bash

# Set the working directory[cite: 1]
WORKDIR /app

# Copy the maven wrapper and build files[cite: 1]
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Fix line endings for Linux[cite: 1]
RUN tr -d '\r' < mvnw > mvnw_unix && mv mvnw_unix mvnw
RUN chmod +x mvnw

# Download dependencies[cite: 1]
RUN ./mvnw dependency:go-offline

# Copy the source and build[cite: 1]
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Find and rename the JAR[cite: 1]
RUN mv target/*.jar app.jar

# Run the app[cite: 1]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]