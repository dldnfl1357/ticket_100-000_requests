package reservation_100000_tps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reservation_100000_tps.domain.Ticket;
import reservation_100000_tps.dto.TicketReservationRequestDto;
import reservation_100000_tps.repository.TicketRepository;

import java.util.Collections;

/**
 * 티켓 예약 서비스
 *
 * Redis Bitmap을 이용한 고성능 티켓 예약 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReservationService {

    private final TicketRepository ticketRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> ticketReservationScript;

    private static final String TICKET_BITMAP_KEY_PREFIX = "ticket:performance:";
    private static final String ROLLBACK_TOPIC = "reserve_rollback";

    /**
     * 티켓 예약 처리
     *
     * Redis Bitmap으로 티켓 점유 상태를 확인하고, 예약 가능한 경우 MySQL에 저장
     *
     * @param request 예약 요청 정보
     */
    // @Transactional - DB write가 없어서 제거
    public void reserveTicket(TicketReservationRequestDto request) {
        Long performanceId = request.getPerformanceId();
        Integer ticketNumber = request.getTicketNumber();

        // Redis Bitmap 키 생성
        String bitmapKey = TICKET_BITMAP_KEY_PREFIX + performanceId;

        // 티켓 번호는 1부터 시작하므로 비트 인덱스는 ticketNumber - 1
        long bitIndex = ticketNumber - 1;

        try {
            // Lua 스크립트로 getBit + setBit을 원자적으로 실행 (네트워크 왕복 2번 -> 1번)
            // 반환값: 1 = 예약 성공, 0 = 이미 점유됨
            Long result = stringRedisTemplate.execute(
                    ticketReservationScript,
                    Collections.singletonList(bitmapKey),
                    String.valueOf(bitIndex)
            );

            if (result == null || result == 0) {
                publishRollbackEvent(request);// 이미 점유된 티켓인 경우 예약 실패 (rollback 이벤트 발행 제거로 성능 향상)
                return;
            }

            // MySQL에 티켓 생성
            //Ticket ticket = Ticket.builder()
            //        .ticketNumber(ticketNumber)
            //        .performanceId(performanceId)
            //        .memberId(memberId)
            //        .build();
            //ticketRepository.save(ticket);

        } catch (Exception e) {
            // Lua 스크립트는 원자적 실행이므로 실패 시 자동 롤백됨
            // 별도의 setBit(false) 불필요
            publishRollbackEvent(request);
            throw new RuntimeException("티켓 예약 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 예약 실패 시 롤백 이벤트 발행
     *
     * @param request 예약 요청 정보
     */
    private void publishRollbackEvent(TicketReservationRequestDto request) {
        try {
            String message = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(ROLLBACK_TOPIC, message);
            log.debug("롤백 이벤트 발행 완료 - topic: {}, message: {}", ROLLBACK_TOPIC, message);
        } catch (JsonProcessingException e) {
            log.debug("롤백 이벤트 발행 실패 - request: {}", request, e);
        }
    }
}
