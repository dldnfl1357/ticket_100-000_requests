#!/bin/bash

echo "=== 성능 측정 스크립트 ==="
echo ""
echo "1. 메시지 발행 시작"
echo "발행 시작: $(date '+%Y-%m-%d %H:%M:%S')"
PUBLISH_START=$(date +%s%3N)

for round in 1 2; do
  for ticket in {1..50000}; do
    echo "{\"member_id\": $((($round-1)*50000 + $ticket)), \"performance_id\": 1, \"ticket_number\": $ticket}"
  done
done | docker exec -i reservation-kafka kafka-console-producer \
  --topic reserve \
  --bootstrap-server localhost:9092

PUBLISH_END=$(date +%s%3N)
echo "발행 완료: $(date '+%Y-%m-%d %H:%M:%S')"
echo "발행 소요 시간: $((PUBLISH_END - PUBLISH_START))ms"

echo ""
echo "2. Consumer 처리 대기 중... (애플리케이션 로그 확인 필요)"
echo "   - IntelliJ나 터미널에서 '총 소요 시간' 로그를 확인하세요"
