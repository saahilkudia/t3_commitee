# We use Temurin JDK 21 because it's stable and professional
FROM eclipse-temurin:21-jdk-alpine

# NEW: Install mysql-client and bash
# This physically puts the 'mysqldump' and 'mysql' tools into your Render server
RUN apk update && apk add --no-cache mysql-client bash

# Set the working directory
WORKDIR /app

# Copy the maven wrapper and pom file
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Fix line endings for Linux
RUN tr -d '\r' < mvnw > mvnw_unix && mv mvnw_unix mvnw
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the source code and build the JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Find the built jar and rename it
RUN mv target/*.jar app.jar

# Run the app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]