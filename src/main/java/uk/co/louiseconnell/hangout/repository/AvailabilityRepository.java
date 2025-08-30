package uk.co.louiseconnell.hangout.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.co.louiseconnell.hangout.entity.Availability;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    
    List<Availability> findByEventId(Long eventId);
    
    List<Availability> findByTimeslotId(Long timeslotId);
    
    Optional<Availability> findByUserDiscordIdAndTimeslotId(String userDiscordId, Long timeslotId);
    
    @Query("SELECT a FROM Availability a WHERE a.event.id = :eventId AND a.user.discordId = :userDiscordId")
    List<Availability> findByEventAndUser(@Param("eventId") Long eventId, @Param("userDiscordId") String userDiscordId);
    
    @Query("SELECT COUNT(a) FROM Availability a WHERE a.timeslot.id = :timeslotId AND a.status = 'AVAILABLE'")
    int countAvailableByTimeslot(@Param("timeslotId") Long timeslotId);
}
