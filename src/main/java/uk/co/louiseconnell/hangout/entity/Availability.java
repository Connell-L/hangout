package uk.co.louiseconnell.hangout.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "availabilities", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_discord_id", "timeslot_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Availability {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_discord_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;
    
    @Column(nullable = false)
    private LocalDateTime votedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityStatus status;
    
    public enum AvailabilityStatus {
        AVAILABLE, MAYBE, UNAVAILABLE
    }
}
