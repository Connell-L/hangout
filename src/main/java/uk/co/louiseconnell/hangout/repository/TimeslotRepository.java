package uk.co.louiseconnell.hangout.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.co.louiseconnell.hangout.entity.Timeslot;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, Long> {
    
    List<Timeslot> findByEventIdOrderByStartTime(Long eventId);
    
    Optional<Timeslot> findByEventIdAndEmoji(Long eventId, String emoji);
    
    @Query("SELECT t FROM Timeslot t WHERE t.event.id = :eventId ORDER BY t.startTime")
    List<Timeslot> findTimeslotsByEventOrdered(@Param("eventId") Long eventId);
}
