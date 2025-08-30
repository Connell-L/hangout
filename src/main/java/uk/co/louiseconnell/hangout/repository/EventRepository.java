package uk.co.louiseconnell.hangout.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.co.louiseconnell.hangout.entity.Event;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    List<Event> findByChannelIdAndStatus(String channelId, Event.EventStatus status);
    
    List<Event> findByCreatorDiscordId(String creatorDiscordId);
    
    Optional<Event> findByMessageId(String messageId);
    
    @Query("SELECT e FROM Event e WHERE e.channelId = :channelId AND e.status = 'ACTIVE' ORDER BY e.createdAt DESC")
    List<Event> findActiveEventsByChannel(@Param("channelId") String channelId);

    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.deadline IS NOT NULL AND e.deadline <= :now")
    List<Event> findDueActiveEvents(@Param("now") LocalDateTime now);
}
