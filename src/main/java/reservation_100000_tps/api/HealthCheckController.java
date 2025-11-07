package reservation_100000_tps.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reservation_100000_tps.dto.ApiResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 헬스 체크 컨트롤러
 *
 * 서버 상태 확인을 위한 API
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthCheckController {

    /**
     * 서버 헬스 체크
     *
     * @return 서버 상태 정보
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("application", "Reservation System");
        health.put("version", "1.0.0");

        return ApiResponse.success(health, "Server is running");
    }
}
