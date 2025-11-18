# 티켓 예약 시스템 동작 프로세스 상세 문서

## 📋 목차
1. [시스템 아키텍처](#시스템-아키텍처)
2. [애플리케이션 초기화](#애플리케이션-초기화)
3. [메시지 발행 프로세스](#메시지-발행-프로세스)
4. [메시지 소비 및 처리 프로세스](#메시지-소비-및-처리-프로세스)
5. [Redis 파이프라이닝 처리](#redis-파이프라이닝-처리)
6. [롤백 처리](#롤백-처리)
7. [성능 최적화 포인트](#성능-최적화-포인트)

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                      Kafka Producer (외부)                       │
│                 100,000개 메시지 발행 (5초 소요)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Kafka Topic: reserve                         │
│                  - 파티션: 20개                                   │
│                  - 메시지: 100,000개                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Kafka Consumer (20개 동시 실행)                      │
│        TicketReservationConsumer.consumeReservation()            │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  배치 리스너: 최대 1000개 메시지를 배치로 수신            │   │
│  │  - max.poll.records: 1000                               │   │
│  │  - concurrency: 20 (20개 컨슈머 스레드)                  │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  TicketReservationService                        │
│                  reserveTicketBatch(requests)                    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Redis 파이프라이닝으로 배치 1000개 한 번에 처리         │   │
│  │  - 네트워크 RTT: 1회                                     │   │
│  │  - Lua Script SHA 사용 (사전 로드됨)                     │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Redis                                   │
│                   Lettuce Client (Netty 기반)                    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Connection Pool:                                        │   │
│  │  - max-active: 500                                       │   │
│  │  - max-idle: 100                                         │   │
│  │  - min-idle: 50                                          │   │
│  │                                                           │   │
│  │  Bitmap 저장:                                             │   │
│  │  - Key: ticket:performance:1                            │   │
│  │  - Bit: 티켓 번호별 점유 상태 (0=미점유, 1=점유)          │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
            ┌───────────┐      ┌──────────────┐
            │ 성공 (50K)│      │  실패 (50K)  │
            └───────────┘      └──────┬───────┘
                                      │
                                      ▼
                        ┌──────────────────────────┐
                        │ Kafka Topic: rollback     │
                        │ - 롤백 이벤트 발행        │
                        └──────────────────────────┘
```

---

## 애플리케이션 초기화

### Phase 1: Spring Boot 애플리케이션 시작

```java
// main() 실행
ReservationApplication.main()
  └─> Spring Context 초기화
      └─> Bean 생성 및 의존성 주입
```

### Phase 2: Redis 설정 초기화

**파일**: `RedisConfig.java`

```java
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // Lettuce Connection Pool 설정
        // - max-active: 500
        // - max-idle: 100
        // - min-idle: 50
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisScript<Long> ticketReservationScript() {
        // Lua Script 정의
        String script =
            "local bit = redis.call('getbit', KEYS[1], ARGV[1]) " +
            "if bit == 0 then " +
            "    redis.call('setbit', KEYS[1], ARGV[1], 1) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

        return RedisScript.of(script, Long.class);
    }
}
```

### Phase 3: TicketReservationService 초기화

**파일**: `TicketReservationService.java`

```java
@PostConstruct
public void init() {
    // 1. Lua 스크립트를 Redis에 로드
    byte[] scriptBytes = ticketReservationScript.getScriptAsString().getBytes();

    // 2. Redis에 스크립트 전송 및 SHA 해시 반환
    scriptSha = stringRedisTemplate.execute(
        (RedisCallback<String>) connection ->
            connection.scriptingCommands().scriptLoad(scriptBytes)
    );
    // scriptSha 예시: "a1b2c3d4e5f6..."

    // 3. 로그 출력
    log.info("Lua 스크립트 로드 완료 - SHA: {}", scriptSha);
}
```

**중요**: 이 스크립트 SHA는 애플리케이션이 살아있는 동안 계속 재사용됩니다.

### Phase 4: Kafka Consumer 설정

**파일**: `KafkaConsumerConfig.java`

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    // Consumer 설정
    factory.setConsumerFactory(consumerFactory());
    factory.setConcurrency(20);  // 20개 컨슈머 스레드 생성
    factory.setBatchListener(true);  // 배치 모드
    factory.getContainerProperties().setAckMode(
        ContainerProperties.AckMode.MANUAL_IMMEDIATE
    );

    return factory;
}
```

**결과**:
- 20개의 Kafka Consumer 스레드 생성
- 각 스레드는 독립적으로 파티션에서 메시지 소비
- reserve 토픽의 20개 파티션에 1:1 매핑

---

## 메시지 발행 프로세스

### Step 1: 외부에서 Kafka 메시지 발행

**스크립트**: `script/0 generate.sh`

```bash
for round in 1 2; do
  for ticket in {1..50000}; do
    echo '{"member_id": '$((($round-1)*50000 + $ticket))',
          "performance_id": 1,
          "ticket_number": '$ticket'}'
  done
done | docker exec -i reservation-kafka kafka-console-producer \
  --topic reserve \
  --bootstrap-server localhost:9092
```

**발행 내역**:
- Round 1: 티켓 1~50,000번 (member_id: 1~50,000)
- Round 2: 티켓 1~50,000번 (member_id: 50,001~100,000)
- **총 100,000개 메시지** (중복 50,000개)

### Step 2: Kafka 파티션 분산

```
reserve Topic (20 Partitions)
├─ Partition 0:  5,000개 메시지
├─ Partition 1:  5,000개 메시지
├─ Partition 2:  5,000개 메시지
│  ...
└─ Partition 19: 5,000개 메시지
```

---

## 메시지 소비 및 처리 프로세스

### Step 1: Kafka Consumer - 메시지 폴링

**파일**: `TicketReservationConsumer.java`

```java
@KafkaListener(topics = "reserve", groupId = "reservation-consumer-group")
public void consumeReservation(List<String> messages, Acknowledgment acknowledgment) {
    // messages: 최대 1000개 메시지 배치
    // messages 예시:
    // [
    //   '{"member_id": 1, "performance_id": 1, "ticket_number": 1}',
    //   '{"member_id": 2, "performance_id": 1, "ticket_number": 2}',
    //   ...
    //   '{"member_id": 1000, "performance_id": 1, "ticket_number": 1000}'
    // ]
```

**Kafka 설정**:
- `max.poll.records: 1000` → 한 번에 최대 1000개
- `concurrency: 20` → 20개 스레드가 동시에 폴링
- **동시 처리량**: 최대 20,000개 메시지 (20 × 1000)

### Step 2: JSON 파싱 → DTO 변환

```java
// 첫 메시지 시작 시간 기록
if (startTime == null) {
    startTime = System.currentTimeMillis();
    processedCount.set(0);
}

// 배치 메시지들을 DTO로 변환
List<TicketReservationRequestDto> requests = new ArrayList<>();
for (String message : messages) {
    try {
        TicketReservationRequestDto request = objectMapper.readValue(
            message,
            TicketReservationRequestDto.class
        );
        requests.add(request);
    } catch (Exception e) {
        log.error("메시지 파싱 중 오류 발생: {}", message, e);
    }
}
```

**변환 결과**:
```java
List<TicketReservationRequestDto> requests = [
    TicketReservationRequestDto(memberId=1, performanceId=1, ticketNumber=1),
    TicketReservationRequestDto(memberId=2, performanceId=1, ticketNumber=2),
    ...
    TicketReservationRequestDto(memberId=1000, performanceId=1, ticketNumber=1000)
]
```

### Step 3: 서비스 레이어 호출

```java
// Redis 파이프라이닝으로 배치 처리
ticketReservationService.reserveTicketBatch(requests);
```

---

## Redis 파이프라이닝 처리

### Step 1: 배치 검증 및 파이프라이닝 시작

**파일**: `TicketReservationService.java:64-91`

```java
public void reserveTicketBatch(List<TicketReservationRequestDto> requests) {
    if (requests == null || requests.isEmpty()) {
        return;
    }

    try {
        // Redis 파이프라이닝으로 배치 실행
        List<Object> results = stringRedisTemplate.executePipelined(
            (RedisCallback<Object>) connection -> {
                // ... 파이프라인 내부 로직
            }
        );
```

### Step 2: 파이프라인 내부 - 명령어 큐잉

```java
(RedisCallback<Object>) connection -> {
    // 배치의 모든 요청을 파이프라인에 큐잉
    for (TicketReservationRequestDto request : requests) {
        Long performanceId = request.getPerformanceId();      // 1
        Integer ticketNumber = request.getTicketNumber();     // 1~50000
        String bitmapKey = TICKET_BITMAP_KEY_PREFIX + performanceId;
        // "ticket:performance:1"

        long bitIndex = ticketNumber - 1;  // 0~49999

        // Lua 스크립트 SHA로 실행 (파이프라인에 큐잉만)
        connection.scriptingCommands().evalSha(
            scriptSha,  // 미리 로드된 SHA (예: "a1b2c3d4...")
            org.springframework.data.redis.connection.ReturnType.INTEGER,
            1,  // KEYS 개수
            bitmapKey.getBytes(),  // KEYS[1] = "ticket:performance:1"
            String.valueOf(bitIndex).getBytes()  // ARGV[1] = "0"~"49999"
        );
    }
    return null; // 파이프라인은 null 반환
}
```

**중요**:
- 이 시점에는 **Redis 명령어가 실행되지 않음**
- 1000개 명령어가 **메모리에 큐잉만** 됨
- `return null` 시점에 **한 번에 Redis로 전송**

### Step 3: Redis 서버에서 실행

```
Client → Redis 서버
┌────────────────────────────────────────────────────┐
│ 1회 네트워크 전송 (파이프라인)                      │
├────────────────────────────────────────────────────┤
│ EVALSHA a1b2c3d4... 1 ticket:performance:1 0       │
│ EVALSHA a1b2c3d4... 1 ticket:performance:1 1       │
│ EVALSHA a1b2c3d4... 1 ticket:performance:1 2       │
│ ...                                                 │
│ EVALSHA a1b2c3d4... 1 ticket:performance:1 999     │
└────────────────────────────────────────────────────┘
         │
         ▼
Redis 서버에서 순차 실행
┌─────────────────────────────────────────────────────┐
│ 각 명령어마다 Lua Script 실행:                      │
│                                                      │
│ 1. local bit = redis.call('getbit', 'ticket:...', 0)│
│ 2. if bit == 0 then                                 │
│ 3.     redis.call('setbit', 'ticket:...', 0, 1)     │
│ 4.     return 1  -- 성공                            │
│ 5. else                                              │
│ 6.     return 0  -- 이미 점유됨                      │
│ 7. end                                               │
└─────────────────────────────────────────────────────┘
         │
         ▼
Redis → Client
┌────────────────────────────────────────────────────┐
│ 1회 네트워크 전송 (결과 반환)                       │
├────────────────────────────────────────────────────┤
│ [1, 1, 1, ..., 0, 0, 0]                            │
│  ↑성공  ↑실패 (이미 점유)                           │
└────────────────────────────────────────────────────┘
```

**성능 핵심**:
- **네트워크 RTT**: 2회 (요청 1회 + 응답 1회)
- **기존 방식**: 2000회 (1000개 × 2)
- **개선율**: 1000배

### Step 4: 결과 처리

```java
// 파이프라인 실행 완료 후 results에 결과 저장
// results = [1, 1, 1, 0, 1, 0, 0, 1, ...]
//            ↑성공   ↑실패

List<TicketReservationRequestDto> failedRequests = new ArrayList<>();
for (int i = 0; i < requests.size(); i++) {
    Long result = (Long) results.get(i);
    if (result == null || result == 0) {
        // 예약 실패 (이미 점유됨)
        failedRequests.add(requests.get(i));
    }
}

// 예시:
// failedRequests = [
//     TicketReservationRequestDto(memberId=50005, performanceId=1, ticketNumber=5),
//     TicketReservationRequestDto(memberId=50010, performanceId=1, ticketNumber=10),
//     ...
// ]
```

---

## 롤백 처리

### Step 1: 실패 건 롤백 이벤트 발행

```java
// 실패 건들 롤백 이벤트 발행
if (!failedRequests.isEmpty()) {
    for (TicketReservationRequestDto failed : failedRequests) {
        publishRollbackEvent(failed);
    }
}
```

### Step 2: Kafka로 롤백 메시지 전송

```java
private void publishRollbackEvent(TicketReservationRequestDto request) {
    try {
        // DTO를 JSON으로 직렬화
        String message = objectMapper.writeValueAsString(request);
        // message = '{"member_id": 50005, "performance_id": 1, "ticket_number": 5}'

        // Kafka 비동기 전송
        kafkaTemplate.send(ROLLBACK_TOPIC, message);
        // ROLLBACK_TOPIC = "reserve_rollback"

        log.debug("롤백 이벤트 발행 완료 - topic: {}, message: {}",
                  ROLLBACK_TOPIC, message);
    } catch (JsonProcessingException e) {
        log.error("롤백 이벤트 발행 실패 - request: {}", request, e);
    }
}
```

**Kafka Producer 설정** (`KafkaProducerConfig.java`):
```java
config.put(ProducerConfig.ACKS_CONFIG, "1");  // 리더만 확인
config.put(ProducerConfig.LINGER_MS_CONFIG, 10);  // 10ms 배치 수집
config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB 배치
config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");  // 압축
```

### Step 3: Kafka 커밋 및 진행 상황 로그

```java
// 배치 단위로 커밋
acknowledgment.acknowledge();

// 처리 완료 카운트
int count = processedCount.addAndGet(messages.size());

// 진행 상황 로그 (1만개 단위 넘어갈 때마다)
int lastLogged = lastLoggedCount.get();
int currentInterval = count / LOG_INTERVAL;  // LOG_INTERVAL = 10000
int lastInterval = lastLogged / LOG_INTERVAL;

if (currentInterval > lastInterval &&
    lastLoggedCount.compareAndSet(lastLogged, count)) {
    long currentTime = System.currentTimeMillis();
    long elapsed = currentTime - startTime;
    log.info("진행: {}건 처리 완료 (경과 시간: {}ms)", count, elapsed);
}

// 목표 개수 도달 시 결과 출력
if (count >= TARGET_COUNT) {  // TARGET_COUNT = 100000
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    double seconds = duration / 1000.0;

    log.info("총 처리 건수: {}", count);
    log.info("총 소요 시간: {}ms ({} 초)", duration, seconds);

    // 다음 테스트를 위해 초기화
    startTime = null;
    processedCount.set(0);
    lastLoggedCount.set(0);
}
```

---

## 성능 최적화 포인트

### 1. Kafka 병렬 처리

```
20개 파티션 × 20개 컨슈머 스레드 = 1:1 매핑
├─ Consumer Thread 1  → Partition 0  (5,000개)
├─ Consumer Thread 2  → Partition 1  (5,000개)
├─ Consumer Thread 3  → Partition 2  (5,000개)
│  ...
└─ Consumer Thread 20 → Partition 19 (5,000개)

동시 처리:
- 각 스레드가 최대 1000개씩 배치로 가져옴
- 20개 스레드 × 1000개 = 20,000개 동시 처리 가능
```

### 2. Redis 파이프라이닝

```
기존 (개별 호출):
Request 1 ──┐
            ├─> Redis (RTT: 1ms) ─> Response 1
Request 2 ──┘
Request 2 ──┐
            ├─> Redis (RTT: 1ms) ─> Response 2
Request 3 ──┘
...
총 시간: 1000 × 1ms = 1000ms

파이프라이닝 (배치 호출):
Request 1 ─┐
Request 2 ─┤
Request 3 ─┼─> Redis (RTT: 1ms) ─> [Response 1, 2, 3, ..., 1000]
...        │
Request 1000┘
총 시간: 1ms

개선율: 1000배
```

### 3. Lua Script SHA 캐싱

```
기존 (매번 로드):
배치 1: scriptLoad(script) + evalSha(sha, ...) = 2 RTT
배치 2: scriptLoad(script) + evalSha(sha, ...) = 2 RTT
...
총 100배치 × 2 RTT = 200 RTT

최적화 (@PostConstruct):
초기화: scriptLoad(script) = 1 RTT
배치 1: evalSha(sha, ...) = 1 RTT
배치 2: evalSha(sha, ...) = 1 RTT
...
총 1 + 100 RTT = 101 RTT

개선율: 약 50%
```

### 4. Lettuce Connection Pool

```
설정:
- max-active: 500
- max-idle: 100
- min-idle: 50

효과:
- 20개 컨슈머가 동시에 Redis 호출 가능
- Connection 재사용으로 핸드셰이크 오버헤드 제거
- Netty 기반 비동기 I/O로 효율적인 리소스 사용
```

### 5. Kafka Batch Listener

```
설정:
- max.poll.records: 1000
- batch listener: true

효과:
- 메시지 1000개를 한 번에 처리
- acknowledge() 1회로 오프셋 커밋
- 컨텍스트 스위칭 감소
```

---

## 전체 처리 흐름 타임라인 (100,000개 메시지)

```
시간축 (초):
0s ────────────────────────────────────────────> 4.1s
│                                                 │
│  [메시지 발행]                                  │
│  0s~5s: 외부에서 100,000개 메시지 발행           │
│                                                 │
│  [동시 처리 시작]                                │
│  0s: 첫 배치 도착 → 20개 컨슈머 동시 처리 시작    │
│  │                                               │
│  ├─ 0.0s~0.5s: 배치 1~20 처리 (20,000개)         │
│  ├─ 0.5s~1.0s: 배치 21~40 처리 (20,000개)        │
│  ├─ 1.0s~1.5s: 배치 41~60 처리 (20,000개)        │
│  ├─ 1.5s~2.0s: 배치 61~80 처리 (20,000개)        │
│  └─ 2.0s~4.1s: 배치 81~100 처리 (20,000개)       │
│                                                 │
│  [로그 출력]                                     │
│  ~1.0s: "진행: 10000건 처리 완료"                │
│  ~2.0s: "진행: 20000건 처리 완료"                │
│  ~3.0s: "진행: 30000건 처리 완료"                │
│  ~4.0s: "진행: 90000건 처리 완료"                │
│  4.1s: "총 처리 건수: 100000"                    │
│  4.1s: "총 소요 시간: 4100ms (4.1 초)"           │
└─────────────────────────────────────────────────┘

처리량 계산:
- 총 100,000개 / 4.1초 = 24,390 TPS
- 성공 50,000개 / 4.1초 = 12,195 TPS (실제 예약)
```

---

## 핵심 성능 지표

| 구분 | 값 |
|------|-----|
| **총 메시지 수** | 100,000개 |
| **성공 예약** | 50,000개 |
| **실패 예약** | 50,000개 (중복) |
| **총 처리 시간** | 4.1초 |
| **전체 TPS** | 24,390 TPS |
| **성공 TPS** | 12,195 TPS |
| **Kafka 컨슈머** | 20개 (동시 실행) |
| **배치 크기** | 최대 1000개 |
| **Redis RTT** | 배치당 1회 |
| **Redis Pool** | 500개 커넥션 |

---

## 에러 처리 및 재시도 로직

### 1. 메시지 파싱 에러
```java
try {
    TicketReservationRequestDto request = objectMapper.readValue(message, ...);
    requests.add(request);
} catch (Exception e) {
    log.error("메시지 파싱 중 오류 발생: {}", message, e);
    // 해당 메시지만 스킵, 나머지 계속 처리
}
```

### 2. Redis 파이프라이닝 에러
```java
try {
    List<Object> results = stringRedisTemplate.executePipelined(...);
} catch (Exception e) {
    log.error("티켓 예약 배치 처리 중 오류 발생", e);
    // 전체 배치 실패 시 모든 요청에 대해 롤백 이벤트 발행
    for (TicketReservationRequestDto request : requests) {
        publishRollbackEvent(request);
    }
}
```

### 3. Kafka 롤백 이벤트 발행 실패
```java
try {
    String message = objectMapper.writeValueAsString(request);
    kafkaTemplate.send(ROLLBACK_TOPIC, message);
} catch (JsonProcessingException e) {
    log.error("롤백 이벤트 발행 실패 - request: {}", request, e);
    // 로그만 남기고 계속 진행 (비즈니스 로직 중단하지 않음)
}
```

---

## 모니터링 포인트

### 1. 애플리케이션 로그
```
2025-11-18 18:00:00 - Lua 스크립트 로드 완료 - SHA: a1b2c3d4...
2025-11-18 18:01:00 - 진행: 10000건 처리 완료 (경과 시간: 1000ms)
2025-11-18 18:01:02 - 진행: 20000건 처리 완료 (경과 시간: 2000ms)
...
2025-11-18 18:01:04 - 총 처리 건수: 100000
2025-11-18 18:01:04 - 총 소요 시간: 4100ms (4.1 초)
```

### 2. Redis 모니터링
```bash
# Redis 연결 확인
redis-cli INFO clients

# Bitmap 확인
redis-cli BITCOUNT ticket:performance:1  # 예상: 50000

# 메모리 사용량
redis-cli INFO memory
```

### 3. Kafka 모니터링
```bash
# Consumer Lag 확인
docker exec reservation-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group reservation-consumer-group \
  --describe

# Topic 메시지 수 확인
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic reserve \
  --time -1
```

---

## 결론

이 시스템은 다음 최적화 기법을 통해 **4.1초** 성능을 달성했습니다:

1. **Kafka 병렬화**: 20개 파티션 × 20개 컨슈머
2. **배치 처리**: 최대 1000개씩 배치로 처리
3. **Redis 파이프라이닝**: 네트워크 RTT 1000배 감소
4. **Lua Script SHA 캐싱**: 초기화 시 1회만 로드
5. **Lettuce Connection Pool**: 500개 커넥션으로 동시성 확보

**최종 성능**: 24,390 TPS (100,000개 / 4.1초)
