package reservation_100000_tps.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reservation_100000_tps.dto.TicketReservationRequestDto;
import reservation_100000_tps.service.TicketReservationService;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 티켓 예약 Kafka Consumer
 *
 * reserve 토픽에서 예약 요청을 받아 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReservationConsumer {

    private final TicketReservationService ticketReservationService;
    private final ObjectMapper objectMapper;

    private Long startTime = null;
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private static final int TARGET_COUNT = 10000;

    /**
     * reserve 토픽 메시지 처리
     *
     * @param message 예약 요청 메시지 (JSON)
     * @param acknowledgment Kafka manual acknowledgment
     */
    @KafkaListener(topics = "reserve", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeReservation(String message, Acknowledgment acknowledgment) {
        try {
            // 첫 메시지 시작 시간 기록
            if (startTime == null) {
                startTime = System.currentTimeMillis();
                processedCount.set(0);
            }

            log.debug("Kafka 메시지 수신 - topic: reserve, message: {}", message);

            // JSON 메시지를 DTO로 변환
            TicketReservationRequestDto request = objectMapper.readValue(
                    message,
                    TicketReservationRequestDto.class
            );

            // 티켓 예약 처리
            ticketReservationService.reserveTicket(request);

            // 수동 커밋
            acknowledgment.acknowledge();

            log.debug("Kafka 메시지 처리 완료 - topic: reserve, message: {}", message);

            // 처리 완료 카운트
            int count = processedCount.incrementAndGet();

            // 목표 개수 도달 시 결과 출력
            if (count == TARGET_COUNT) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                double seconds = duration / 1000.0;

                log.info("총 처리 건수: {}", count);
                log.info("총 소요 시간: {}ms ({} 초)", duration, seconds);

                // 다음 테스트를 위해 초기화
                startTime = null;
                processedCount.set(0);
            }

        } catch (Exception e) {
            log.error("Kafka 메시지 처리 실패 - topic: reserve, message: {}", message, e);
            // 에러 발생 시에도 acknowledge하여 메시지 재처리 방지
            // (이미 서비스 레이어에서 롤백 이벤트를 발행했으므로)
            acknowledgment.acknowledge();
        }
    }
}
