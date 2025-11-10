package reservation_100000_tps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import reservation_100000_tps.domain.Ticket;

import java.util.Optional;

/**
 * 티켓 Repository
 *
 * 티켓 데이터 접근을 위한 인터페이스
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
