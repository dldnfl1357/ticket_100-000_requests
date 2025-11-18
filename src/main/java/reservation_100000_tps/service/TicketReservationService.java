package reservation_100000_tps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reservation_100000_tps.dto.TicketReservationRequestDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 티켓 예약 서비스
 *
 * Redis Bitmap을 이용한 고성능 티켓 예약 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReservationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> ticketReservationScript;

    private static final String TICKET_BITMAP_KEY_PREFIX = "ticket:performance:";
    private static final String ROLLBACK_TOPIC = "reserve_rollback";

    // 애플리케이션 시작 시 한 번만 로드한 스크립트 SHA
    private String scriptSha;

    /**
     * 애플리케이션 시작 시 Lua 스크립트를 Redis에 로드
     */
    @PostConstruct
    public void init() {
        byte[] scriptBytes = ticketReservationScript.getScriptAsString().getBytes();
        scriptSha = stringRedisTemplate.execute(
            (RedisCallback<String>) connection ->
                connection.scriptingCommands().scriptLoad(scriptBytes)
        );

        if (scriptSha == null) {
            throw new RuntimeException("Lua 스크립트 로드 실패");
        }

        log.info("Lua 스크립트 로드 완료 - SHA: {}", scriptSha);
    }

    /**
     * 티켓 예약 배치 처리 (Redis 파이프라이닝 사용)
     *
     * 배치로 받은 요청들을 한 번에 Redis에 전송하여 네트워크 RTT 최소화
     *
     * @param requests 예약 요청 리스트
     */
    public void reserveTicketBatch(List<TicketReservationRequestDto> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        try {
            // Redis 파이프라이닝으로 배치 실행
            List<Object> results = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    // 배치의 모든 요청을 파이프라인에 큐잉
                    for (TicketReservationRequestDto request : requests) {
                        Long performanceId = request.getPerformanceId();
                        Integer ticketNumber = request.getTicketNumber();
                        String bitmapKey = TICKET_BITMAP_KEY_PREFIX + performanceId;
                        long bitIndex = ticketNumber - 1;

                        // 미리 로드된 스크립트 SHA로 실행 (파이프라인에 큐잉)
                        connection.scriptingCommands().evalSha(
                            scriptSha,
                            org.springframework.data.redis.connection.ReturnType.INTEGER,
                            1,
                            bitmapKey.getBytes(),
                            String.valueOf(bitIndex).getBytes()
                        );
                    }
                    return null; // 파이프라인은 null 반환
                }
            );

            // 결과 확인 및 실패 건 롤백 이벤트 발행
            List<TicketReservationRequestDto> failedRequests = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                Long result = (Long) results.get(i);
                if (result == null || result == 0) {
                    // 예약 실패 (이미 점유됨)
                    failedRequests.add(requests.get(i));
                }
            }

            // 실패 건들 롤백 이벤트 발행
            if (!failedRequests.isEmpty()) {
                for (TicketReservationRequestDto failed : failedRequests) {
                    publishRollbackEvent(failed);
                }
            }

        } catch (Exception e) {
            log.error("티켓 예약 배치 처리 중 오류 발생", e);
            // 전체 배치 실패 시 모든 요청에 대해 롤백 이벤트 발행
            for (TicketReservationRequestDto request : requests) {
                publishRollbackEvent(request);
            }
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
            log.error("롤백 이벤트 발행 실패 - request: {}", request, e);
        }
    }
}
