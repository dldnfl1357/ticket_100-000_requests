#!/bin/bash
# reserve 토픽 삭제 후 재생성
echo "[Kafka] reserve 토픽 재생성..."
docker exec reservation-kafka kafka-topics \
  --delete \
  --topic reserve \
  --bootstrap-server localhost:9092 2>/dev/null || true

sleep 2

docker exec reservation-kafka kafka-topics \
  --create \
  --topic reserve \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

echo "[Kafka] reserve 토픽 삭제 완료!"

# reserve_rollback 토픽 삭제 후 재생성
echo "[Kafka] reserve_rollback 토픽 재생성..."
docker exec reservation-kafka kafka-topics \
  --delete \
  --topic reserve_rollback \
  --bootstrap-server localhost:9092 2>/dev/null || true

sleep 2

docker exec reservation-kafka kafka-topics \
  --create \
  --topic reserve_rollback \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

echo "[Kafka] reserve_rollback 토픽 삭제 완료!"