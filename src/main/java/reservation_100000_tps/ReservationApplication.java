package reservation_100000_tps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 티켓 예매 시스템 메인 애플리케이션
 *
 * 목표: 100,000 TPS + 에러 0% + 응답 시간 3초 이내
 */
@SpringBootApplication
@EnableJpaAuditing
public class ReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }
}
