@echo off

REM Spring Boot 실행 스크립트 (메모리 최적화) - Windows

REM JVM 옵션 설정
set JAVA_OPTS=-Xms12g -Xmx16g ^
  -XX:+UseG1GC ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:+ParallelRefProcEnabled ^
  -XX:+UseStringDeduplication ^
  -XX:+OptimizeStringConcat ^
  -server

echo Starting Reservation System with optimized JVM settings...
echo Memory: -Xms12g -Xmx16g
echo GC: G1GC with 200ms max pause time
echo.

REM Gradle로 실행
gradlew.bat bootRun --args="--spring.profiles.active=dev"
