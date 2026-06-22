# ---- Build stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
<<<<<<< HEAD
=======
# Сваля dependencies преди копиране на кода (кеш слой)
>>>>>>> cdee426ca730c459e29dca307bfef66915a56434
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

<<<<<<< HEAD
ENTRYPOINT ["sh", "-c", "java \
  -Djava.security.egd=file:/dev/./urandom \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Dserver.port=${PORT:-8080} \
  -jar app.jar"]
=======
# Render инжектира PORT автоматично
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
>>>>>>> cdee426ca730c459e29dca307bfef66915a56434
