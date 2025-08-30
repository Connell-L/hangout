package uk.co.louiseconnell.hangout.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimezoneUtil {
    
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Convert UTC time to user's timezone
     */
    public static LocalDateTime convertToUserTimezone(LocalDateTime utcTime, ZoneId userTimezone) {
        if (utcTime == null || userTimezone == null) {
            return utcTime;
        }
        
        ZonedDateTime utcZoned = utcTime.atZone(ZoneId.of("UTC"));
        return utcZoned.withZoneSameInstant(userTimezone).toLocalDateTime();
    }
    
    /**
     * Convert user timezone to UTC
     */
    public static LocalDateTime convertToUtc(LocalDateTime userTime, ZoneId userTimezone) {
        if (userTime == null || userTimezone == null) {
            return userTime;
        }
        
        ZonedDateTime userZoned = userTime.atZone(userTimezone);
        return userZoned.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }
    
    /**
     * Format datetime for Discord display with timezone info
     */
    public static String formatForDiscord(LocalDateTime dateTime, ZoneId timezone) {
        if (dateTime == null) {
            return "TBD";
        }
        
        LocalDateTime userTime = convertToUserTimezone(dateTime, timezone);
        return userTime.format(DISPLAY_FORMAT) + " " + timezone.getId();
    }
    
    /**
     * Format time range for Discord display
     */
    public static String formatTimeRange(LocalDateTime start, LocalDateTime end, ZoneId timezone) {
        if (start == null || end == null) {
            return "TBD";
        }
        
        LocalDateTime userStart = convertToUserTimezone(start, timezone);
        LocalDateTime userEnd = convertToUserTimezone(end, timezone);
        
        if (userStart.toLocalDate().equals(userEnd.toLocalDate())) {
            // Same day
            return userStart.format(DISPLAY_FORMAT) + " - " + userEnd.format(TIME_FORMAT) + " " + timezone.getId();
        } else {
            // Different days
            return userStart.format(DISPLAY_FORMAT) + " - " + userEnd.format(DISPLAY_FORMAT) + " " + timezone.getId();
        }
    }
}
