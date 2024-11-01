# Use an official OpenJDK image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY build/libs/phone-0.0.1-SNAPSHOT.jar app.jar

# Expose the port that the app will run on
EXPOSE 8080

# Command to run the app
CMD ["java", "-jar", "app.jar"]
