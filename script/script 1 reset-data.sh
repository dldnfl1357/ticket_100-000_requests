# mysql ticket 테이블에서 삭제
echo "1. [Mysql] ticket에서 performance_id가 1인 데이터 전체 삭제..."
docker exec reservation-mysql mysql \
  -uroot -ppassword \
  reservation \
  -e "DELETE FROM ticket WHERE performance_id = 1;"
echo "[Mysql] ticket에서 performance_id가 1인 데이터 전체 삭제 완료!"

# redis bitmaps 삭제
echo "2. [Redis] ticket:performance bitmaps 삭제..."
docker exec reservation-redis redis-cli \
  DEL ticket:performance:1

echo "[Redis] ticket:performance bitmaps 삭제 완료!"

# reserve 토픽 삭제 후 재생성
echo "3. [Kafka] reserve 토픽 재생성..."
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
echo "4. [Kafka] reserve_rollback 토픽 재생성..."
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