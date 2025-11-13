echo "=== 티켓 예약 부하 테스트 시작 ==="
START_TIME=$(date +%s)

for round in 1 2; do
  echo "Round $round: 티켓 1~5,000 발행 중..." >&2
  for ticket in {1..5000}; do
    echo "{\"member_id\": $((($round-1)*5000 + $ticket)), \"performance_id\": 1, \"ticket_number\": $ticket}"
  done
done | docker exec -i reservation-kafka kafka-console-producer \
  --topic reserve \
  --bootstrap-server localhost:9092

END_TIME=$(date +%s)
echo "메시지 발행 완료 (소요 시간: $((END_TIME - START_TIME))초)"
