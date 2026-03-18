@echo off
cd /d %~dp0

echo Spring Boot ...
call mvnw.cmd spring-boot:run

pause