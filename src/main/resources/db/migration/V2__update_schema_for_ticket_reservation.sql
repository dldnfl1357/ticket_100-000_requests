-- V2 Schema Update
-- 티켓 예매 시스템 스키마 업데이트

-- 1. reservation 테이블 삭제
DROP TABLE IF EXISTS reservation;

-- 2. 기존 ticket 테이블 삭제 (새로운 구조로 재생성하기 위해)
DROP TABLE IF EXISTS ticket;

-- 3. performance 테이블 생성
CREATE TABLE performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    number_of_audience INT NOT NULL COMMENT '해당 공연의 좌석 수',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공연';

-- 4. performance 초기 데이터 삽입 (id=1, number_of_audience=50000)
INSERT INTO performance (id, number_of_audience) VALUES (1, 50000);

-- 5. ticket 테이블을 Ticket 엔터티에 맞게 재생성
CREATE TABLE ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    ticket_number INT NOT NULL COMMENT '티켓 번호 (1부터 공연 좌석 수만큼)',
    performance_id BIGINT NOT NULL COMMENT '해당 공연 ID',
    member_id BIGINT NOT NULL COMMENT '티켓을 구매한 멤버 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_performance_ticket (performance_id, ticket_number),
    INDEX idx_member (member_id),
    CONSTRAINT fk_ticket_performance FOREIGN KEY (performance_id) REFERENCES performance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='티켓';
