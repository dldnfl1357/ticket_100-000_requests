# 티켓 예약 시스템 테스트 가이드

## 테스트 목표

100,000개의 예약 요청을 처리하여 다음을 검증:
- **정확성**: 50,000개 좌석에 정확히 50,000개 티켓만 예약
- **성능**: 처리 시간 3초 이내
- **일관성**: Redis Bitmap과 MySQL 데이터 일치

---

## 테스트 일지
1. 카프카 컨슈머 로직 -> redis 조회 및 set -> mysql 저장: 수 분 소요
2. 최적화1
   1. 작업1. db 저장 동작 제거
   2. 처리 시간: 60초 소요
3. 최적화2
   1. 작업1. 카프카 컨슈머 병렬성 증가(3 -> 10)
   2. 처리 시간: 56초
       1. 원인: 카프카 동시성이 커지니까 redis에서 병목
4. 최적화3
   1. 작업1. 로그 변경 INFO -> DEBUG 2000개 로그 제거
   2. 작업2. 트랜잭션 제거
   3. 작업3. 카프카 동시성 10 -> 5
   4. 작업4. Redis 속성 변경
      1. THP 비활성화
      2. 동시 연결 대기열을 1024로 상향
      3. 파일 디스크립터 제한을 65536으로 상향
      4. 최대 메모리 8GB로 상향
      5. 메모리 부족 시 가장 오래된 키 삭제 활성화
      6. 영속성 비활성화
   5. 작업5. Redis를 Lua Script로 실행
   6. 처리 시간: 25초
5. 최적화4
   1. 작업1. 카프카에서 명확한 10개 동시 실행(카프카 동시성을 10으로 올리고 파티션 수까지 10으로 맞춤)
   2. 작업2. Consume 동작을 배치로 여러 개씩 처리
   3. 처리 시간: 12초
6. 최적화5
   1. 작업1. 컨슈머 병렬러 받아서 for문으로 동기 처리하지 않고 또 다시 병렬 처리하도록 수정
   2. 작업2. 파티션을 20까지 늘려서 동시성 상향
   3. 처리 시간: 7.3초
7. 최적화6
   1. 작업1. Redis 파이프라이닝 적용
      1. 배치로 받은 1000개 메시지를 개별 Redis 호출하지 않고 파이프라이닝으로 한 번에 전송
      2. 네트워크 RTT: 1000번 → 1번으로 감소
   2. 작업2. CompletableFuture 병렬 처리 제거
      1. parallelStream() 사용 시 ForkJoinPool 블로킹 에러 발생
      2. 파이프라이닝으로 배치 처리하므로 개별 병렬 처리 불필요
   3. 작업3. Lua 스크립트 SHA 캐싱
      1. @PostConstruct에서 애플리케이션 시작 시 한 번만 스크립트 로드
      2. 배치 처리마다 scriptLoad 호출 제거 (네트워크 RTT 1회 감소)
   4. 처리 시간: 4.1초
7. 최적화7
   1. 작업1. Kafka Pool 최댓값 상향 1000 -> 2000
   2. 작업2. 롤백(메시지 발행) 부분을 비동기로 변경(CompletableFuture)
   4. 처리 시간: 평균 3.5초

---

## 사전 준비

### 1. 인프라 확인
```bash
# Docker 컨테이너 상태 확인
docker ps --filter name=reservation

# 필요한 컨테이너: reservation-mysql, reservation-redis, reservation-kafka, reservation-zookeeper
```

### 2. 애플리케이션 실행
```bash
# 애플리케이션이 실행 중이어야 함
# Java 17 환경에서 실행
./gradlew bootRun

# 또는 빌드 후 실행
./gradlew build
java -jar build/libs/reservation-*.jar
```

### 3. 로그 모니터링 준비 (별도 터미널)
```bash
# 애플리케이션 로그
tail -f logs/application.log

# 또는 Docker 로그 (Docker로 실행하는 경우)
docker logs -f reservation-app
```

---

## 테스트 절차

### Step 1: 데이터 초기화

모든 테스트 데이터를 삭제하여 깨끗한 상태로 시작합니다.

```bash
# 1) MySQL 데이터 초기화
'script 1-1 reset-mysql.sh' 실행

# 2) Redis 데이터 초기화
'script 1-2 reset-redis.sh' 실행

# 3) Kafka 리셋
'script 1-3 reset-kafka.sh' 실행

```

### Step 2: 부하 발생

티켓 번호 1~50,000을 **각각 2번씩** 발행하여 총 100,000개 메시지를 생성합니다.

#### 방법 1: Bash 스크립트 (권장)

# 티켓 1~50,000을 2번 반복
'script 0 generate-load (publish records to reserve topic).sh' 실행

# 1. 실행 권한 부여
chmod +x 'script 0 generate-load (publish records to reserve topic).sh'
# 2. 실행
./'script 0 generate-load (publish records to reserve topic).sh'

---

## 검증 (Validation)

### 1. MySQL 데이터 검증
#### 1-1. 티켓 총 개수 확인
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT COUNT(*) as total_tickets FROM ticket WHERE performance_id = 1;"
```
**기대값**: `50000`


### 2. Kafka 데이터 검증
#### 2-1. reserve 토픽에 레코드 수 확인
```
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
--broker-list localhost:9092 \
--topic reserve \
--time -1
```
**기대값**: 100,000 (발행된 요청 수)

#### 2-2. reserve_rollback 토픽에 레코드 수 확인
```
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
--broker-list localhost:9092 \
--topic reserve_rollback \
--time -1
```
**기대값**: 50,000 (실패로 인한 롤백)

---

## 성공 기준 (Pass Criteria)

| 검증 항목 | 기대값 | 설명 |
|----------|--------|------|
| MySQL 티켓 개수 | 50,000 | 정확히 50,000개 티켓 |
| 중복 티켓 | 0 | 중복 예약 없음 |
| 티켓 번호 범위 | 1 ~ 50,000 | 전체 범위 커버 |
| 처리 시간 | ≤ 3초 | 첫 티켓~마지막 티켓 시간 차이 |
| Redis Bitmap 카운트 | 50,000 | Redis에 50,000개 비트 설정 |
| Redis-MySQL 일치 | 동일 | 데이터 일관성 |
| Consumer LAG | 0 | 모든 메시지 처리 완료 |
| reserve_rollback 메시지 | 50,000 | 실패한 중복 예약 수 |

---
