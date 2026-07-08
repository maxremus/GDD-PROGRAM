FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# mariadb-client осигурява mysqldump — нужен за автоматичния backup на базата.
#
# ВАЖНО: ако при backup получите грешка
#   "Plugin caching_sha2_password could not be loaded"
# това НЕ е проблем в образа — mariadb-client просто няма тази MySQL 8 auth
# библиотека и никой Debian/Ubuntu пакет не я предоставя отделно в стабилните
# репозиторита. Решението е да смените auth plugin-а на вашия MySQL потребител
# (напр. в Aiven конзолата или през mysql клиент) към mysql_native_password:
#
#   ALTER USER 'вашия_потребител'@'%' IDENTIFIED WITH mysql_native_password BY 'същата_парола';
#   FLUSH PRIVILEGES;
#
# Това е стандартна, широко документирана и безопасна промяна — mysql-connector-j
# (JDBC драйверът на приложението) поддържа и двата плъгина еднакво добре.
RUN apk add --no-cache mariadb-client

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Dserver.port=${PORT:-8080}","-jar","app.jar"]
