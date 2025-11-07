# Reservation System

고성능/고가용 티켓 예매 서버 (목표: 100,000 TPS + 에러 0% + 응답 시간 3초 이내)

## 기술 스택

- **Java 17** (OpenJDK)
- **Spring Boot 3.1.3**
- **Gradle 8.x**
- **MySQL 8.0**
- **Redis**
- **Kafka**
- **Spring Data JPA + Hibernate + QueryDSL**
- **Flyway** (Database Migration)

## 프로젝트 구조

```
src/main/java/reservation_100000_tps/
├── api/                    # REST Controllers
├── service/               # Business Logic
├── domain/               # Domain Entities
├── repository/           # Data Access
├── dto/                  # Data Transfer Objects
├── enums/               # Enumerations
├── configure/           # Configuration (Kafka, Redis, QueryDSL 등)
└── common/              # Common Utilities

src/main/resources/
├── application.yml       # Application Configuration
└── db/migration/        # Flyway Migration Scripts
```

## 시작하기

### 사전 요구사항

1. **Java 17 설치**
   ```bash
   # OpenJDK 17 설치 (Ubuntu/Debian)
   sudo apt update
   sudo apt install openjdk-17-jdk

   # 설치 확인
   java -version
   ```

2. **Gradle Wrapper 초기화**
   ```bash
   # Java 설치 후 실행
   gradle wrapper --gradle-version 8.4
   ```

3. **MySQL 8.0 설치 및 데이터베이스 생성**
   ```bash
   # Docker를 사용하는 경우
   docker run -d \
     --name mysql-reservation \
     -e MYSQL_ROOT_PASSWORD=password \
     -e MYSQL_DATABASE=reservation \
     -p 3306:3306 \
     mysql:8.0
   ```

4. **Redis 설치**
   ```bash
   # Docker를 사용하는 경우
   docker run -d \
     --name redis-reservation \
     -p 6379:6379 \
     redis:latest
   ```

5. **Kafka 설치**
   ```bash
   # Docker를 사용하는 경우 (docker-compose 권장)
   # docker-compose.yml 파일 생성 후 실행
   docker-compose up -d
   ```

### 빌드 및 실행

1. **프로젝트 빌드**
   ```bash
   ./gradlew clean build
   ```

2. **애플리케이션 실행**
   ```bash
   # 기본 프로필로 실행
   ./gradlew bootRun

   # 특정 프로필로 실행
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

3. **JAR 파일로 실행**
   ```bash
   java -jar build/libs/reservation-0.0.1-SNAPSHOT.jar
   ```

### 헬스 체크

서버가 정상적으로 실행되면 다음 URL로 헬스 체크 가능:

```bash
curl http://localhost:8080/api/v1/health
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2025-11-07T13:35:00",
    "application": "Reservation System",
    "version": "1.0.0"
  },
  "message": "Server is running"
}
```

## 설정

### application.yml

주요 설정 항목:

- **Database**: `src/main/resources/application.yml`에서 MySQL 연결 정보 수정
- **Redis**: Redis 호스트 및 포트 설정
- **Kafka**: Kafka 브로커 주소 및 Consumer 설정

### 프로필

- `dev`: 개발 환경 (SQL 로깅 활성화, 디버그 모드)
- `prod`: 운영 환경 (최소 로깅, 성능 최적화)

프로필 변경:
```bash
# application.yml에서
spring.profiles.active: dev

# 또는 실행 시
java -jar app.jar --spring.profiles.active=prod
```

## 데이터베이스 마이그레이션

Flyway를 사용하여 데이터베이스 스키마를 관리합니다.

마이그레이션 스크립트 위치: `src/main/resources/db/migration/`

새 마이그레이션 추가:
```
V{version}__{description}.sql
예: V2__add_user_table.sql
```

## 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests ClassName

# 테스트 커버리지 확인
./gradlew test jacocoTestReport
```

## API 문서

Spring REST Docs를 사용하여 API 문서를 자동 생성합니다.

빌드 후 문서 위치: `build/docs/asciidoc/`

## 라이선스

MIT License
