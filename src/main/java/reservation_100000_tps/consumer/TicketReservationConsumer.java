package reservation_100000_tps.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reservation_100000_tps.dto.TicketReservationRequestDto;
import reservation_100000_tps.service.TicketReservationService;

import java.util.ArrayList;
import java.util.List;
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
    private final AtomicInteger lastLoggedCount = new AtomicInteger(0);
    private static final int TARGET_COUNT = 100000;
    private static final int LOG_INTERVAL = 10000;

    /**
     * reserve 토픽 메시지 배치 처리
     *
     * @param messages 예약 요청 메시지 리스트 (JSON)
     * @param acknowledgment Kafka batch acknowledgment
     */
    @KafkaListener(topics = "reserve", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeReservation(List<String> messages, Acknowledgment acknowledgment) {
        try {
            // 첫 메시지 시작 시간 기록
            if (startTime == null) {
                startTime = System.currentTimeMillis();
                processedCount.set(0);
            }

            // 배치 메시지들을 DTO로 변환
            List<TicketReservationRequestDto> requests = new ArrayList<>();
            for (String message : messages) {
                try {
                    TicketReservationRequestDto request = objectMapper.readValue(
                            message,
                            TicketReservationRequestDto.class
                    );
                    requests.add(request);
                } catch (Exception e) {
                    log.error("메시지 파싱 중 오류 발생: {}", message, e);
                }
            }

            // Redis 파이프라이닝으로 배치 처리
            ticketReservationService.reserveTicketBatch(requests);

            // 배치 단위로 커밋
            acknowledgment.acknowledge();

            // 처리 완료 카운트
            int count = processedCount.addAndGet(messages.size());

            // 진행 상황 로그 (1만개 단위 넘어갈 때마다)
            int lastLogged = lastLoggedCount.get();
            int currentInterval = count / LOG_INTERVAL;
            int lastInterval = lastLogged / LOG_INTERVAL;

            if (currentInterval > lastInterval && lastLoggedCount.compareAndSet(lastLogged, count)) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;
                log.info("진행: {}건 처리 완료 (경과 시간: {}ms)", count, elapsed);
            }

            // 목표 개수 도달 시 결과 출력
            if (count >= TARGET_COUNT) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                double seconds = duration / 1000.0;

                log.info("총 처리 건수: {}", count);
                log.info("총 소요 시간: {}ms ({} 초)", duration, seconds);

                // 다음 테스트를 위해 초기화
                startTime = null;
                processedCount.set(0);
                lastLoggedCount.set(0);
            }

        } catch (Exception e) {
            // 에러 발생 시에도 acknowledge하여 메시지 재처리 방지
            acknowledgment.acknowledge();
        }
    }
}
