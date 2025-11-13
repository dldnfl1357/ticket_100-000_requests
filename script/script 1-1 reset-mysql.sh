#!/bin/bash
# mysql ticket 테이블에서 삭제
echo ". [Mysql] ticket에서 performance_id가 1인 데이터 전체 삭제..."
docker exec reservation-mysql mysql \
  -uroot -ppassword \
  reservation \
  -e "DELETE FROM ticket WHERE performance_id = 1;"
echo "[Mysql] ticket에서 performance_id가 1인 데이터 전체 삭제 완료!"

