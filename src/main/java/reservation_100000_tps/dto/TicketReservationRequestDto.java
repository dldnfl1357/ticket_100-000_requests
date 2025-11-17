package reservation_100000_tps.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 티켓 예약 요청 DTO
 *
 * Kafka reserve 토픽으로 전달되는 예약 요청 정보
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketReservationRequestDto {

    /**
     * 멤버 ID
     */
    @JsonProperty("member_id")
    private Long memberId;

    /**
     * 공연 ID
     */
    @JsonProperty("performance_id")
    private Long performanceId;

    /**
     * 티켓 번호
     */
    @JsonProperty("ticket_number")
    private Integer ticketNumber;
}
