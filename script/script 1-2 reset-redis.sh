#!/bin/bash
# redis bitmaps 삭제
echo "[Redis] ticket:performance bitmaps 삭제..."
docker exec reservation-redis redis-cli \
  DEL ticket:performance:1

echo "[Redis] ticket:performance bitmaps 삭제 완료!"
