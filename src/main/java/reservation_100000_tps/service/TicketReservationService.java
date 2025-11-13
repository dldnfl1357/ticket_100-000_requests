package reservation_100000_tps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reservation_100000_tps.domain.Ticket;
import reservation_100000_tps.dto.TicketReservationRequestDto;
import reservation_100000_tps.repository.TicketRepository;

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
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TICKET_BITMAP_KEY_PREFIX = "ticket:performance:";
    private static final String ROLLBACK_TOPIC = "reserve_rollback";

    /**
     * 티켓 예약 처리
     *
     * Redis Bitmap으로 티켓 점유 상태를 확인하고, 예약 가능한 경우 MySQL에 저장
     *
     * @param request 예약 요청 정보
     */
    @Transactional
    public void reserveTicket(TicketReservationRequestDto request) {
        Long performanceId = request.getPerformanceId();
        Integer ticketNumber = request.getTicketNumber();
        Long memberId = request.getMemberId();

        log.info("티켓 예약 요청 - performanceId: {}, ticketNumber: {}, memberId: {}",
                performanceId, ticketNumber, memberId);

        // Redis Bitmap 키 생성
        String bitmapKey = TICKET_BITMAP_KEY_PREFIX + performanceId;

        // 티켓 번호는 1부터 시작하므로 비트 인덱스는 ticketNumber - 1
        long bitIndex = ticketNumber - 1;

        try {
            // Redis Bitmap에서 해당 티켓이 이미 점유되어 있는지 확인
            Boolean isOccupied = redisTemplate.opsForValue().getBit(bitmapKey, bitIndex);

            if (Boolean.TRUE.equals(isOccupied)) {
                // 이미 점유된 티켓인 경우 예약 실패
                log.warn("티켓 예약 실패 - 이미 점유된 티켓 - performanceId: {}, ticketNumber: {}",
                        performanceId, ticketNumber);
                publishRollbackEvent(request);
                return;
            }

            // Redis Bitmap을 true로 설정하여 티켓 점유 표시
            redisTemplate.opsForValue().setBit(bitmapKey, bitIndex, true);

            // MySQL에 티켓 생성
            //Ticket ticket = Ticket.builder()
            //        .ticketNumber(ticketNumber)
            //        .performanceId(performanceId)
            //        .memberId(memberId)
            //        .build();
            //ticketRepository.save(ticket);

            log.debug("티켓 예약 성공 - performanceId: {}, ticketNumber: {}, memberId: {}",
                    performanceId, ticketNumber, memberId);

        } catch (Exception e) {
            // 예외 발생 시 롤백 이벤트 발행
            log.error("티켓 예약 중 오류 발생 - performanceId: {}, ticketNumber: {}",
                    performanceId, ticketNumber, e);

            // Redis Bitmap 롤백 (점유 상태를 false로 변경)
            redisTemplate.opsForValue().setBit(bitmapKey, bitIndex, false);

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
            log.info("롤백 이벤트 발행 완료 - topic: {}, message: {}", ROLLBACK_TOPIC, message);
        } catch (JsonProcessingException e) {
            log.error("롤백 이벤트 발행 실패 - request: {}", request, e);
        }
    }
}
