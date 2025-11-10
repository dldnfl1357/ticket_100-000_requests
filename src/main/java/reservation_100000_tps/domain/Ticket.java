package reservation_100000_tps.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티켓 엔터티
 *
 * 공연 좌석 예매 정보를 관리
 */
@Entity
@Table(name = "ticket", indexes = {
        @Index(name = "idx_performance_ticket", columnList = "performance_id, ticket_number"),
        @Index(name = "idx_member", columnList = "member_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 티켓 번호 (1부터 공연 좌석 수만큼 증가)
     * 예: 좌석이 50,000개면 1~50,000
     */
    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    /**
     * 해당 공연 ID
     */
    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    /**
     * 티켓을 구매한 멤버 ID
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Builder
    public Ticket(Integer ticketNumber, Long performanceId, Long memberId) {
        this.ticketNumber = ticketNumber;
        this.performanceId = performanceId;
        this.memberId = memberId;
    }
}
