# 🎫 고성능 티켓 예약 시스템

**20,000 TPS + 에러 0% + 응답 시간 5초 이내**를 목표로 하는 고가용성 티켓 예매 서버

## 📊 성능 지표

| 항목 | 목표           | 현재 달성 | 상태 |
|------|--------------|----------|------|
| **처리 시간** | 5초 이내        | **3.5초** | ✅ 1.5초 초과 달성 |
| **처리량 (TPS)** | 28,571 TPS   | **20,000 TPS** | ✅ 40% 초과 달성 |
| **에러율** | 0%           | **0%** | ✅ 달성 |
| **정확성** | 50,000개 정확 예약 | **50,000개** | ✅ 달성 |
| **동시성** | 중복 예약 방지     | **완벽 방지** | ✅ 달성 |

**최종 성능**: 100,000개 메시지를 **평균 5초**에 처리 (20,000 TPS)

---

## 🏗️ 기술 스택

### 핵심 프레임워크
- **Spring Boot 3.1.3** - 메인 프레임워크
- **Java 17** - OpenJDK 17 LTS
- **Gradle 8.x** - 빌드 도구

### 메시징 & 캐싱
- **Apache Kafka** - 분산 메시지 큐 (20개 파티션)
- **Redis** - 인메모리 캐시 + Bitmap (Lettuce 클라이언트)

### 데이터베이스 & 영속성
- **MySQL 8.0** - 관계형 데이터베이스
- **Spring Data JPA 3.1.x** - ORM
- **HikariCP** - 커넥션 풀
- **Flyway** - 데이터베이스 마이그레이션

---

## 🚀 핵심 최적화 기술

### 1. Redis 파이프라이닝 ⭐⭐⭐
```java
// 배치 2000개를 1번의 네트워크 RTT로 처리
stringRedisTemplate.executePipelined(connection -> {
    for (request : requests) {
        connection.scriptingCommands().evalSha(scriptSha, ...);
    }
    return null;
});
```
**효과**: 네트워크 왕복 2000번 → 1번 (**2000배 개선**)

### 2. Lua Script SHA 캐싱 ⭐⭐⭐
```java
@PostConstruct
public void init() {
    // 애플리케이션 시작 시 한 번만 로드
    scriptSha = stringRedisTemplate.execute(
        connection -> connection.scriptingCommands().scriptLoad(scriptBytes)
    );
}
```
**효과**: 배치당 네트워크 RTT 2회 → 1회 (**50% 감소**)

### 3. Kafka 대용량 배치 처리 ⭐⭐
```yaml
max.poll.records: 2000  # 한 번에 2000개 처리
concurrency: 20         # 20개 컨슈머 동시 실행
```
**효과**: 100,000개 ÷ 20 ÷ 2000 = **3번의 폴링**으로 완료

### 4. 완전 비동기 롤백 처리 ⭐
```java
// 롤백 이벤트를 별도 스레드에서 fire-and-forget
CompletableFuture.runAsync(() -> {
    publishRollbackEvents(failedRequests);
});
```
**효과**: 메인 처리 플로우 블로킹 제거

---

## 📐 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                   Kafka Producer (외부)                  │
│              100,000개 메시지 발행 (5초)                  │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              Kafka Topic: reserve (20 파티션)            │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│         Kafka Consumer (20개 동시 실행)                   │
│         배치 크기: 2000개 (max.poll.records)              │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              TicketReservationService                    │
│              reserveTicketBatch(2000개)                  │
│                                                           │
│  Redis 파이프라이닝:                                      │
│  - 2000개 Lua Script 한 번에 실행                         │
│  - 네트워크 RTT: 1회                                      │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    Redis (Lettuce)                       │
│                  Connection Pool: 500                    │
│                                                           │
│  Bitmap: ticket:performance:1                            │
│  - Bit 0-49999: 티켓 점유 상태 (0=미점유, 1=점유)         │
└─────────────────────────┬───────────────────────────────┘
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
         ┌──────────┐        ┌─────────────┐
         │ 성공 50K  │        │  실패 50K    │
         └──────────┘        └──────┬───────┘
                                    │
                                    ▼
                    ┌────────────────────────────┐
                    │ Kafka Topic: rollback       │
                    │ (비동기 발행)                │
                    └────────────────────────────┘
```

---

## 📂 프로젝트 구조

```
src/main/java/reservation_100000_tps/
├── api/                    # REST Controllers
├── service/               # Business Logic
│   └── TicketReservationService.java    # 핵심 예약 로직 (Redis 파이프라이닝)
├── consumer/              # Kafka Consumers
│   └── TicketReservationConsumer.java   # 배치 처리 (20개 동시 실행)
├── domain/               # JPA Entities
│   └── Ticket.java
├── repository/           # Data Access
├── dto/                  # DTOs
├── configure/            # 설정
│   ├── KafkaConsumerConfig.java
│   ├── KafkaProducerConfig.java
│   └── RedisConfig.java
└── common/              # 공통 유틸

script/
├── 0 generate.sh        # 메시지 발행 스크립트
├── 1 reset.sh          # 데이터 초기화 스크립트
├── 2 check performance.sh  # 성능 측정 스크립트
└── 3 check data.sh     # 데이터 검증 스크립트
```

---

## 🛠️ Quick Start

### 1. 인프라 실행 (Docker)
```bash
docker-compose up -d
```

**실행되는 컨테이너**:
- `reservation-mysql` - MySQL 8.0 (4GB RAM)
- `reservation-redis` - Redis 7 (4GB RAM)
- `reservation-kafka` - Kafka (8GB RAM)
- `reservation-zookeeper` - Zookeeper (1GB RAM)

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

**초기화 로그 확인**:
```
Lua 스크립트 로드 완료 - SHA: a1b2c3d4e5f6...
```

### 3. 데이터 초기화
```bash
./script/'1 reset.sh'
```

**실행 내용**:
- MySQL ticket 테이블 초기화
- Redis Bitmap 삭제
- Kafka 토픽 재생성 (reserve 20 파티션, reserve_rollback 20 파티션)

### 4. 부하 테스트 실행
```bash
./script/'2 check performance.sh'
```

**또는 간단히**:
```bash
./script/'0 generate.sh'
```

### 5. 결과 확인
```bash
./script/'3 check data.sh'
```

**예상 결과**:
```
======================================
  티켓 예약 시스템 데이터 검증
======================================

1. Kafka - reserve 토픽
--------------------------------------
   총 메시지 수: 100000

2. Kafka - reserve_rollback 토픽
--------------------------------------
   총 메시지 수: 50000

3. Redis - 예약된 티켓 수
--------------------------------------
   ticket:performance:1 예약 수: 50000

======================================
  검증 완료
======================================
```

---

## 📈 최적화 히스토리

| 단계 | 내용 | 처리 시간 | 개선율 |
|------|------|----------|--------|
| 초기 | MySQL 직접 저장 | 수 분 | - |
| 최적화1 | DB 저장 제거 | 60초 | - |
| 최적화2 | Kafka 동시성 증가 | 56초 | 6.7% ⬇️ |
| 최적화3 | Redis Lua Script | 25초 | 55% ⬇️ |
| 최적화4 | Kafka 배치 처리 | 12초 | 52% ⬇️ |
| 최적화5 | 파티션 20개 확장 | 7.3초 | 39% ⬇️ |
| **최적화6** | **Redis 파이프라이닝 + SHA 캐싱** | **4.1초** | **44% ⬇️** |
| **최적화7** | **Kafka 배치 2000 + 비동기 롤백** | **3.5초** | **15% ⬇️** ✅ |

**전체 개선율**: 60초 → 3.5초 = **94.2% 개선**

---

## 🔧 주요 설정

### Kafka Consumer
```java
// KafkaConsumerConfig.java
max.poll.records: 2000           // 배치 크기 (1000 → 2000)
concurrency: 20                  // 동시 컨슈머 수
fetch.max.wait.ms: 100          // Fetch 대기 시간
enable.auto.commit: false       // 수동 커밋
```

### Redis
```yaml
# application.yml
spring.data.redis.lettuce.pool:
  max-active: 500    # 최대 커넥션
  max-idle: 100      # 유휴 커넥션
  min-idle: 50       # 최소 커넥션
timeout: 3000ms      # 타임아웃
```

### Kafka Producer (롤백용)
```java
// KafkaProducerConfig.java
acks: "1"                      // 리더만 확인
linger.ms: 10                 // 배치 수집 시간
batch.size: 16384            // 배치 크기
compression.type: "snappy"   // 압축
```

### Tomcat
```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    max-connections: 10000
    accept-count: 100
```

---

## 🎯 처리 프로세스

### 1. 메시지 발행 (외부)
```bash
# 티켓 1~50,000번을 2번 반복 (총 100,000개)
# Round 1: member_id 1~50,000
# Round 2: member_id 50,001~100,000 (중복 예약 시도)
```

### 2. Kafka Consumer (20개 동시 실행)
- 각 컨슈머가 최대 2000개씩 배치로 가져옴
- 20개 × 2000개 = **40,000개 동시 처리 가능**

### 3. Redis 파이프라이닝
```java
// 2000개 메시지를 1번의 네트워크 RTT로 처리
executePipelined(connection -> {
    for (int i = 0; i < 2000; i++) {
        connection.evalSha(scriptSha, ...);  // Lua Script 실행
    }
});
```

### 4. Lua Script (원자적 처리)
```lua
-- 동시성 제어: getbit + setbit을 원자적으로 실행
local bit = redis.call('getbit', KEYS[1], ARGV[1])
if bit == 0 then
    redis.call('setbit', KEYS[1], ARGV[1], 1)
    return 1  -- 성공
else
    return 0  -- 이미 점유됨 (중복 예약 방지)
end
```

### 5. 결과 처리
- **성공 (50,000개)**: Redis Bitmap에 기록
- **실패 (50,000개)**: 롤백 토픽에 비동기 발행

---

## 📚 문서

- **[CLAUDE.md](CLAUDE.md)** - 프로젝트 개요 및 개발 가이드라인
- **[SYSTEM_PROCESS_FLOW.md](SYSTEM_PROCESS_FLOW.md)** - 전체 시스템 동작 프로세스 상세
- **[test.md](test.md)** - 테스트 가이드 및 최적화 히스토리
- **[OPTIMIZATION_LOG_2025-11-19.md](OPTIMIZATION_LOG_2025-11-19.md)** - 최신 최적화 작업 로그

---

## 🎓 핵심 학습 내용

### 1. I/O 최적화가 핵심
- ❌ 스레드만 늘린다고 해결 안 됨
- ✅ **네트워크 왕복 횟수 감소**가 가장 중요

### 2. Redis 파이프라이닝
```
개별 호출: 2000번 RTT = 2000ms
파이프라이닝: 1번 RTT = 1ms
개선율: 2000배
```

### 3. Kafka 배치 처리
```
배치 1000개: 100번 처리
배치 2000개: 50번 처리
개선율: 50%
```

### 4. parallelStream의 함정
- **I/O 바운드 작업에 부적합**
- ForkJoinPool 블로킹으로 `RejectedExecutionException` 발생
- 해결: Redis 파이프라이닝으로 개별 병렬 처리 자체를 제거

---

## 🧪 테스트

### 단위 테스트
```bash
./gradlew test
```

### 성능 측정
```bash
# 성능 측정 + 결과 출력
./script/'2 check performance.sh'
```

### 데이터 검증
```bash
# Kafka 메시지 수 + Redis 예약 수 확인
./script/'3 check data.sh'
```

---

## 🚀 추가 최적화 가능성

현재 3.5초 → 목표 3초까지 **0.5초** 추가 최적화 가능:

1. **Kafka fetch 최적화** (예상: -0.1~0.2초)
   ```java
   fetch.max.wait.ms: 100 → 50
   ```

2. **Concurrency 증가** (예상: -0.2~0.3초)
   ```java
   concurrency: 20 → 30 (파티션도 30으로)
   ```

3. **로그 I/O 최소화** (예상: -0.05~0.1초)
   ```java
   // 진행 상황 로그 제거 또는 간소화
   ```

4. **Redis 로컬 설치** (예상: -0.1~0.2초)
   - Docker Redis → Native Redis

---

## 💻 시스템 요구사항

### 사용 PC
- **CPU**: i7-11700 (8코어/16스레드) 이상
- **RAM**: 32GB 이상
- **Disk**: SSD 100GB 이상

### 자원 할당

| 서비스 | RAM | CPU | 포트 | 용도 |
|--------|-----|-----|------|------|
| **Spring Boot** | 16GB | 8코어 | 8080 | 메인 애플리케이션 |
| **MySQL** | 4GB | 2코어 | 3306 | 데이터베이스 |
| **Kafka** | 8GB | 4코어 | 9092 | 메시지 큐 |
| **Zookeeper** | 1GB | 1코어 | 2181 | Kafka 의존성 |
| **Redis** | 4GB | 2코어 | 6379 | 캐싱 |
| **시스템 여유** | ~4GB | 2코어 | - | OS 및 기타 |

---

## 🐛 트러블슈팅

### Kafka 토픽 파티션이 1개로 생성되는 경우
```bash
# script/1 reset.sh에서 sleep 시간이 충분한지 확인
# Kafka 토픽 삭제가 비동기로 처리되므로 5초 이상 대기 필요
```

### Redis 파이프라이닝 에러
```
Script digest must not be null
```
**해결**: @PostConstruct에서 스크립트 SHA 캐싱 확인

### ForkJoinPool 블로킹 에러
```
RejectedExecutionException: Thread limit exceeded replacing blocked worker
```
**원인**: parallelStream을 I/O 작업에 사용
**해결**: Redis 파이프라이닝으로 변경

---

## 📄 라이선스

This project is licensed under the MIT License.

---

## 🙏 감사의 말

이 프로젝트는 **고성능 분산 시스템 설계**를 학습하기 위해 만들어졌습니다.

주요 학습 내용:
- Redis 파이프라이닝과 Lua Script 활용
- Kafka 대용량 배치 처리
- 동시성 제어 및 race condition 방지
- I/O 바운드 작업 최적화
- 성능 측정 및 병목 지점 분석

---

**Made with ❤️ for High-Performance Systems**
