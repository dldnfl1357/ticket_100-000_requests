# Reservation System

고성능/고가용 티켓 예매 서버 (목표: 100,000 TPS + 에러 0% + 응답 시간 3초 이내)

## 기술 스택

- **Java 17** (OpenJDK)
- **Spring Boot 3.1.3**
- **Gradle 8.x**
- **MySQL 8.0**
- **Redis**
- **Kafka**
- **Spring Data JPA**
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

## 시스템 요구사항 및 자원 할당

### 권장 사양
- **CPU**: i7-11700 (8코어/16스레드) 이상
- **RAM**: 32GB 이상
- **Disk**: SSD 100GB 이상

### 자원 할당 (i7-11700 + 32GB RAM 기준)

| 서비스 | RAM | CPU | 포트 | 용도 |
|--------|-----|-----|------|------|
| **Spring Boot** | 16GB | 8코어 | 8080 | 메인 애플리케이션 |
| **MySQL** | 4GB | 2코어 | 3306 | 데이터베이스 |
| **Kafka** | 8GB | 4코어 | 9092 | 메시지 큐 |
| **Zookeeper** | 1GB | 1코어 | 2181 | Kafka 의존성 |
| **Redis** | 4GB | 2코어 | 6379 | 캐싱/분산락 |
| **시스템 여유** | ~4GB | 2코어 | - | OS 및 기타 |

## Quick Start

전체 시스템을 빠르게 시작하는 방법:

```bash
# 1. 인프라 실행 (MySQL, Redis, Kafka)
docker-compose up -d

# 2. Spring Boot 애플리케이션 실행 (최적화된 JVM 설정)
./run.sh   # Linux/Mac
run.bat    # Windows

# 3. 헬스 체크
curl http://localhost:8080/api/v1/health
```

## 시작하기

### 1. 사전 요구사항

**Java 17 설치**
```bash
# OpenJDK 17 설치 (Ubuntu/Debian)
sudo apt update
sudo apt install openjdk-17-jdk

# 설치 확인
java -version
```

**Docker & Docker Compose 설치**
```bash
# Docker 설치 확인
docker --version
docker-compose --version
```

**Gradle Wrapper 초기화**
```bash
# Java 설치 후 실행
gradle wrapper --gradle-version 8.4
```

### 2. 인프라 실행 (Docker Compose)

**모든 인프라를 한 번에 실행** (MySQL, Redis, Kafka, Zookeeper)

```bash
# 백그라운드 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 상태 확인
docker-compose ps

# 중지
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

**개별 서비스 상태 확인**
```bash
# MySQL
docker exec -it reservation-mysql mysql -uroot -ppassword -e "SHOW DATABASES;"

# Redis
docker exec -it reservation-redis redis-cli PING

# Kafka
docker exec -it reservation-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### 3. 애플리케이션 실행

**방법 1: 최적화된 실행 스크립트 사용 (권장)**

자원 할당이 최적화된 JVM 옵션으로 실행:
- Heap: 12GB ~ 16GB
- GC: G1GC (Max Pause 200ms)

```bash
# Linux/Mac
./run.sh

# Windows
run.bat
```

**방법 2: 직접 실행**

```bash
# 기본 실행
./gradlew bootRun

# 프로필 지정
./gradlew bootRun --args='--spring.profiles.active=dev'

# JVM 옵션 커스터마이징
JAVA_OPTS="-Xms12g -Xmx16g -XX:+UseG1GC" ./gradlew bootRun
```

**방법 3: JAR 파일로 실행**

```bash
# 빌드
./gradlew clean build

# 실행
java -Xms12g -Xmx16g -XX:+UseG1GC \
  -jar build/libs/reservation-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev
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

## 성능 모니터링

### 시스템 자원 사용량 확인

**Docker 컨테이너 자원 사용량**
```bash
# 실시간 모니터링
docker stats

# 특정 컨테이너만
docker stats reservation-mysql reservation-kafka reservation-redis
```

**Spring Boot JVM 메모리 사용량**
```bash
# JVM 프로세스 확인
jps -v

# 힙 메모리 상세 정보
jmap -heap <pid>

# GC 로그 실시간 확인 (애플리케이션 실행 시 옵션 추가 필요)
tail -f logs/gc.log
```

### 포트 사용 확인

```bash
# Linux
netstat -tuln | grep -E "8080|3306|6379|9092"

# Windows
netstat -ano | findstr "8080 3306 6379 9092"
```

## 트러블슈팅

### 포트 충돌 발생 시

```bash
# 사용 중인 포트 확인 및 프로세스 종료
# Linux
sudo lsof -i :8080
sudo kill -9 <PID>

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Docker 컨테이너 재시작

```bash
# 특정 컨테이너 재시작
docker restart reservation-mysql
docker restart reservation-kafka
docker restart reservation-redis

# 모든 컨테이너 재시작
docker-compose restart
```

### 메모리 부족 시

**JVM 힙 메모리 줄이기** (16GB → 8GB)
```bash
# run.sh 또는 run.bat에서 수정
-Xms8g -Xmx8g
```

**Docker 컨테이너 메모리 줄이기**
```yaml
# docker-compose.yml에서 각 서비스의 limits 수정
limits:
  memory: 2G  # 4G에서 2G로 변경
```

## 라이선스

MIT License
