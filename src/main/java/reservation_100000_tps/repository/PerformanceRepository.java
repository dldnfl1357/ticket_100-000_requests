package reservation_100000_tps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import reservation_100000_tps.domain.Performance;

/**
 * 공연 Repository
 *
 * 공연 데이터 접근을 위한 인터페이스
 */
@Repository
public interface PerformanceRepository extends JpaRepository<Performance, Long> {
}
