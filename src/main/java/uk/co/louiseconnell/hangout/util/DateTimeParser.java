package uk.co.louiseconnell.hangout.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DateTimeParser {

  private static final List<DateTimeFormatter> FORMATTERS = new ArrayList<>();
  static {
    // Common patterns
    FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("MMM d yyyy HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"));
    FORMATTERS.add(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"));
  }

  public static LocalDateTime parseToUtc(String input, ZoneId userZone) {
    if (input == null) {
      return null;
    }
    String s = input.trim();

    // Today/Tomorrow helpers (e.g., "today 19:00", "tomorrow 08:30")
    if (s.toLowerCase().startsWith("today ")) {
      String timePart = s.substring(6).trim();
      LocalTime t = LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"));
      LocalDateTime userLdt = LocalDate.now(userZone).atTime(t);
      return TimezoneUtil.convertToUtc(userLdt, userZone);
    }
    if (s.toLowerCase().startsWith("tomorrow ")) {
      String timePart = s.substring(9).trim();
      LocalTime t = LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"));
      LocalDateTime userLdt = LocalDate.now(userZone).plusDays(1).atTime(t);
      return TimezoneUtil.convertToUtc(userLdt, userZone);
    }

    // Day-of-week helpers (e.g., "fri 19:00", "monday 08:30") -> next occurrence
    String lower = s.toLowerCase();
    String[] parts = lower.split("\\s+");
    if (parts.length == 2 && parts[1].matches("\\d{1,2}:\\d{2}")) {
      java.time.DayOfWeek dow = switch (parts[0]) {
        case "mon", "monday" -> java.time.DayOfWeek.MONDAY;
        case "tue", "tues", "tuesday" -> java.time.DayOfWeek.TUESDAY;
        case "wed", "weds", "wednesday" -> java.time.DayOfWeek.WEDNESDAY;
        case "thu", "thur", "thurs", "thursday" -> java.time.DayOfWeek.THURSDAY;
        case "fri", "friday" -> java.time.DayOfWeek.FRIDAY;
        case "sat", "saturday" -> java.time.DayOfWeek.SATURDAY;
        case "sun", "sunday" -> java.time.DayOfWeek.SUNDAY;
        default -> null;
      };
      if (dow != null) {
        java.time.LocalTime t = java.time.LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm"));
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(userZone);
        int currentDow = now.getDayOfWeek().getValue();
        int targetDow = dow.getValue();
        int daysAhead = (targetDow - currentDow + 7) % 7;
        if (daysAhead == 0 && now.toLocalTime().isAfter(t)) {
          daysAhead = 7;
        }
        java.time.LocalDate date = now.toLocalDate().plusDays(daysAhead);
        LocalDateTime userLdt = date.atTime(t);
        return TimezoneUtil.convertToUtc(userLdt, userZone);
      }
    }

    // Try full date-time patterns
    for (DateTimeFormatter fmt : FORMATTERS) {
      try {
        LocalDateTime ldt = LocalDateTime.parse(s, fmt);
        return TimezoneUtil.convertToUtc(ldt, userZone);
      } catch (Exception ignored) { }
    }

    // Fallback: try ISO_LOCAL_DATE_TIME without seconds
    try {
      LocalDateTime ldt = LocalDateTime.parse(s);
      return TimezoneUtil.convertToUtc(ldt, userZone);
    } catch (Exception ignored) {
    }

    throw new DateTimeParseException("Unrecognized date/time format", s, 0);
  }
}
