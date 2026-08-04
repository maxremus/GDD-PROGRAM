FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# mariadb-client осигурява mysqldump — нужен за автоматичния backup на базата.
# mariadb-plugin-caching-sha2-password е ЗАДЪЛЖИТЕЛЕН, защото MySQL 8 (Aiven и др.)
# ползва caching_sha2_password като auth plugin по подразбиране, а базовият
# mariadb-client го няма вграден (води до грешка "Plugin caching_sha2_password
# could not be loaded"). Alpine-базовият образ НЕ предлага този пакет, затова
# минаваме на Debian/Ubuntu-базов образ (eclipse-temurin:17-jre-jammy).
RUN apt-get update && \
    apt-get install -y --no-install-recommends mariadb-client mariadb-plugin-caching-sha2-password && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Dserver.port=${PORT:-8080}","-jar","app.jar"]
