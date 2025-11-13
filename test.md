# 티켓 예약 시스템 테스트 가이드

## 테스트 목표

100,000개의 예약 요청을 처리하여 다음을 검증:
- **정확성**: 50,000개 좌석에 정확히 50,000개 티켓만 예약
- **성능**: 처리 시간 3초 이내
- **일관성**: Redis Bitmap과 MySQL 데이터 일치

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

#### 1-2. 중복 티켓 확인
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT ticket_number, COUNT(*) as cnt
      FROM ticket
      WHERE performance_id = 1
      GROUP BY ticket_number
      HAVING cnt > 1;"
```

**기대값**: `Empty set` (중복 없음)

#### 1-3. 티켓 번호 범위 확인
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT MIN(ticket_number) as min_ticket, MAX(ticket_number) as max_ticket
      FROM ticket
      WHERE performance_id = 1;"
```

**기대값**: `min_ticket=1, max_ticket=50000`

#### 1-4. 티켓 번호 완전성 확인 (빠진 번호 없는지)
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT COUNT(DISTINCT ticket_number) as distinct_tickets
      FROM ticket
      WHERE performance_id = 1;"
```

**기대값**: `50000` (1~50000 전체)

#### 1-5. 처리 시간 확인
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT
    MIN(created_at) as first_ticket_time,
    MAX(created_at) as last_ticket_time,
    TIMESTAMPDIFF(SECOND, MIN(created_at), MAX(created_at)) as duration_seconds
  FROM ticket
  WHERE performance_id = 1;"
```

**기대값**: `duration_seconds <= 3` (3초 이내)

#### 1-6. 멤버별 예약 현황
```bash
docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -e "SELECT COUNT(DISTINCT member_id) as total_members
      FROM ticket
      WHERE performance_id = 1;"
```
**기대값**: `50000` (각 멤버가 1개씩 예약)

```sql
SELECT
    MIN(created_at) AS min_time,
    MAX(created_at) AS max_time,
    TIMESTAMPDIFF(SECOND, MIN(created_at), MAX(created_at)) AS diff_seconds
  FROM ticket
  WHERE performance_id = 1;
```
**기대값**: `3` (가장 먼저 들어온 티켓과 늦게 들어온 티켓의 생성 시간의 차가 3초 이내!)

### 2. Redis 데이터 검증

#### 2-1. Bitmap 총 카운트
```bash
docker exec reservation-redis redis-cli BITCOUNT ticket:performance:1
```
**기대값**: `50000`

#### 2-2. 특정 티켓 상태 확인
```bash
# 티켓 1번 상태 (예약됨)
docker exec reservation-redis redis-cli GETBIT ticket:performance:1 0

# 티켓 50000번 상태 (예약됨)
docker exec reservation-redis redis-cli GETBIT ticket:performance:1 49999
```
**기대값**: 모두 `1` (예약됨)

#### 2-4. Redis와 MySQL 일치 확인
```bash
# Redis 카운트
REDIS_COUNT=$(docker exec reservation-redis redis-cli BITCOUNT ticket:performance:1)

# MySQL 카운트
MYSQL_COUNT=$(docker exec reservation-mysql mysql -uroot -ppassword reservation \
  -sN -e "SELECT COUNT(*) FROM ticket WHERE performance_id = 1;")

echo "Redis: $REDIS_COUNT"
echo "MySQL: $MYSQL_COUNT"

if [ "$REDIS_COUNT" = "$MYSQL_COUNT" ]; then
  echo "✅ 일치: Redis와 MySQL 데이터 동일"
else
  echo "❌ 불일치: Redis($REDIS_COUNT) != MySQL($MYSQL_COUNT)"
fi
```
**기대값**: 두 값이 동일 (`50000`)

### 3. Kafka 검증

#### 3-1. reserve 토픽 Consumer LAG
```bash
docker exec reservation-kafka kafka-consumer-groups \
  --describe \
  --group reservation-consumer-group \
  --bootstrap-server localhost:9092
```
**기대값**: 모든 파티션의 `LAG = 0`

#### 3-2. reserve 토픽 총 LAG 계산
```bash
docker exec reservation-kafka kafka-consumer-groups \
  --describe \
  --group reservation-consumer-group \
  --bootstrap-server localhost:9092 | \
  awk 'NR>1 {sum+=$6} END {print "Total LAG:", sum}'
```
**기대값**: `Total LAG: 0`

#### 3-3. reserve_rollback 토픽 메시지 개수
```bash
# 각 파티션의 오프셋 확인
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic reserve_rollback \
  --time -1

# 총 메시지 개수 계산
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic reserve_rollback \
  --time -1 | \
  awk -F: '{sum+=$3} END {print "Total messages:", sum}'
```
**기대값**: `Total messages: 50000` (실패한 중복 예약)

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
