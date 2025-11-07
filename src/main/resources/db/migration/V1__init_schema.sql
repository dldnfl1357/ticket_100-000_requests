-- Initial Schema
-- 티켓 예매 시스템 초기 스키마

-- 티켓 테이블 (예시)
CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '티켓 이름',
    total_quantity INT NOT NULL DEFAULT 0 COMMENT '전체 수량',
    remaining_quantity INT NOT NULL DEFAULT 0 COMMENT '잔여 수량',
    price DECIMAL(10, 2) NOT NULL COMMENT '가격',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_is_active (is_active),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='티켓';

-- 예약 테이블 (예시)
CREATE TABLE IF NOT EXISTS reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL COMMENT '티켓 ID',
    user_id VARCHAR(100) NOT NULL COMMENT '사용자 ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '수량',
    total_price DECIMAL(10, 2) NOT NULL COMMENT '총 가격',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '예약 상태 (PENDING, CONFIRMED, CANCELLED)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_reservation_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='예약';
