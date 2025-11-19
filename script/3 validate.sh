#!/bin/bash
echo "기대값:"
echo "  - reserve 토픽: 100000"
echo "  - reserve_rollback 토픽: 50000"
echo "  - Redis 예약 수: 50000"
echo "-------------------------"

echo "실제값:"
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic reserve \
  --time -1 | awk -F: '{sum += $3} END {print "  - reserve 토픽:",sum}'
docker exec reservation-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic reserve_rollback \
  --time -1 | awk -F: '{sum += $3} END {print "  - reserve_rollback 토픽:",sum}'
BITCOUNT=$(docker exec reservation-redis redis-cli BITCOUNT ticket:performance:1)
echo "  - Redis 예약 수: $BITCOUNT"

