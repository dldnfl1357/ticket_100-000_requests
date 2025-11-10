package reservation_100000_tps.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연 엔터티
 *
 * 공연 정보 및 좌석 수를 관리
 */
@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 해당 공연의 좌석 수
     */
    @Column(name = "number_of_audience", nullable = false)
    private Integer numberOfAudience;

    @Builder
    public Performance(Integer numberOfAudience) {
        this.numberOfAudience = numberOfAudience;
    }
}
