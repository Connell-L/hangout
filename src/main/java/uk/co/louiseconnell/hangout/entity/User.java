package uk.co.louiseconnell.hangout.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZoneId;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    
    @Id
    private String discordId; // Discord user ID as primary key
    
    @Column(nullable = false)
    private String username;
    
    @Column
    private String displayName;
    
    @Column
    private String timezone; // User's timezone (e.g., "America/New_York")
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Availability> availabilities;
    
    public ZoneId getZoneId() {
        return timezone != null ? ZoneId.of(timezone) : ZoneId.of("UTC");
    }
}
