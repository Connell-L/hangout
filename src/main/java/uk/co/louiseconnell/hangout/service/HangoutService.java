package uk.co.louiseconnell.hangout.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.louiseconnell.hangout.entity.Availability;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.entity.Timeslot;
import uk.co.louiseconnell.hangout.entity.User;
import uk.co.louiseconnell.hangout.repository.AvailabilityRepository;
import uk.co.louiseconnell.hangout.repository.EventRepository;
import uk.co.louiseconnell.hangout.repository.TimeslotRepository;
import uk.co.louiseconnell.hangout.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HangoutService {

  private final EventRepository eventRepository;
  private final TimeslotRepository timeslotRepository;
  private final AvailabilityRepository availabilityRepository;
  private final UserRepository userRepository;

  private static final String[] NUMBER_EMOJIS = new String[] {
      "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"
  };

  @Transactional
  public Event createHangoutEvent(String title,
                                  String description,
                                  String creatorDiscordId,
                                  String channelId,
                                  LocalDateTime deadline,
                                  List<TimeslotRequest> timeslotRequests) {
    if (timeslotRequests == null || timeslotRequests.isEmpty()) {
      throw new IllegalArgumentException("At least one timeslot is required");
    }

    Event event = Event.builder()
        .title(title)
        .description(description)
        .creatorDiscordId(creatorDiscordId)
        .channelId(channelId)
        .createdAt(LocalDateTime.now())
        .deadline(deadline)
        .status(Event.EventStatus.ACTIVE)
        .build();
    event = eventRepository.save(event);

    // Persist timeslots with numeric emojis
    int idx = 0;
    List<Timeslot> saved = new ArrayList<>();
    for (TimeslotRequest req : timeslotRequests) {
      String emoji = idx < NUMBER_EMOJIS.length ? NUMBER_EMOJIS[idx] : NUMBER_EMOJIS[NUMBER_EMOJIS.length - 1];
      Timeslot t = Timeslot.builder()
          .event(event)
          .startTime(req.startTime())
          .endTime(req.endTime())
          .description(req.description())
          .emoji(emoji)
          .build();
      saved.add(timeslotRepository.save(t));
      idx++;
    }

    event.setTimeslots(new java.util.HashSet<>(saved));
    return event;
  }

  @Transactional
  public Event createDraftEvent(String title,
                                String description,
                                String creatorDiscordId,
                                String channelId) {
    Event event = Event.builder()
        .title(title)
        .description(description)
        .creatorDiscordId(creatorDiscordId)
        .channelId(channelId)
        .createdAt(LocalDateTime.now())
        .status(Event.EventStatus.DRAFT)
        .build();
    return eventRepository.save(event);
  }

  @Transactional
  public Timeslot addTimeslotToEvent(Long eventId, TimeslotRequest req) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    if (event.getStatus() != Event.EventStatus.DRAFT && event.getStatus() != Event.EventStatus.ACTIVE) {
      throw new IllegalStateException("Can only add timeslots to DRAFT or ACTIVE events");
    }
    // Determine next available emoji
    List<Timeslot> existing = timeslotRepository.findTimeslotsByEventOrdered(eventId);
    String emoji = NUMBER_EMOJIS[Math.min(existing.size(), NUMBER_EMOJIS.length - 1)];
    Timeslot t = Timeslot.builder()
        .event(event)
        .startTime(req.startTime())
        .endTime(req.endTime())
        .description(req.description())
        .emoji(emoji)
        .build();
    return timeslotRepository.save(t);
  }

  @Transactional
  public Event finalizeDraftToActive(Long eventId) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    if (event.getStatus() != Event.EventStatus.DRAFT) {
      throw new IllegalStateException("Event is not a draft");
    }
    List<Timeslot> slots = timeslotRepository.findTimeslotsByEventOrdered(eventId);
    if (slots.isEmpty()) {
      throw new IllegalStateException("No timeslots proposed for this draft");
    }
    // Pick most popular (AVAILABLE count)
    Timeslot winner = slots.stream()
        .max((a, b) -> Integer.compare(getAvailabilityCount(a.getId()), getAvailabilityCount(b.getId())))
        .orElse(slots.get(0));

    // Remove non-winning timeslots and their availabilities
    for (Timeslot slot : slots) {
      if (!slot.getId().equals(winner.getId())) {
        List<Availability> votes = availabilityRepository.findByTimeslotId(slot.getId());
        if (!votes.isEmpty()) {
          availabilityRepository.deleteAll(votes);
        }
        timeslotRepository.delete(slot);
      }
    }

    // Mark active
    event.setStatus(Event.EventStatus.ACTIVE);
    eventRepository.save(event);
    return event;
  }

  @Transactional
  public void updateEventMessageId(Long eventId, String messageId) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    event.setMessageId(messageId);
    eventRepository.save(event);
  }

  @Transactional(readOnly = true)
  public List<Timeslot> getTimeslotsByEvent(Long eventId) {
    return timeslotRepository.findTimeslotsByEventOrdered(eventId);
  }

  @Transactional(readOnly = true)
  public Optional<Event> getEventByMessageId(String messageId) {
    return eventRepository.findByMessageId(messageId);
  }

  @Transactional(readOnly = true)
  public Optional<Event> getEventById(Long id) {
    return eventRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<Timeslot> findTimeslotByEmoji(Long eventId, String emoji) {
    return timeslotRepository.findByEventIdAndEmoji(eventId, emoji);
  }

  @Transactional
  public void voteForTimeslot(String userDiscordId, Long timeslotId, Availability.AvailabilityStatus status) {
    Timeslot timeslot = timeslotRepository.findById(timeslotId)
        .orElseThrow(() -> new IllegalArgumentException("Timeslot not found: " + timeslotId));

    // Ensure user exists or create minimal record
    User user = userRepository.findById(userDiscordId)
        .orElseGet(() -> userRepository.save(User.builder()
            .discordId(userDiscordId)
            .username(userDiscordId)
            .build()));

    // If user already has a vote for this timeslot, update it; otherwise create
    Optional<Availability> existing = availabilityRepository.findByUserDiscordIdAndTimeslotId(userDiscordId, timeslotId);
    Availability availability = existing.orElseGet(() -> Availability.builder()
        .user(user)
        .event(timeslot.getEvent())
        .timeslot(timeslot)
        .build());

    availability.setStatus(status);
    availability.setVotedAt(LocalDateTime.now());
    availabilityRepository.save(availability);
  }

  @Transactional
  public void removeUserVote(String userDiscordId, Long timeslotId) {
    availabilityRepository.findByUserDiscordIdAndTimeslotId(userDiscordId, timeslotId)
        .ifPresent(availabilityRepository::delete);
  }

  @Transactional
  public void removeAllUserVotes(String userDiscordId, Long eventId) {
    List<Availability> votes = availabilityRepository.findByEventAndUser(eventId, userDiscordId);
    if (!votes.isEmpty()) {
      availabilityRepository.deleteAll(votes);
    }
  }

  @Transactional(readOnly = true)
  public int getAvailabilityCount(Long timeslotId) {
    return availabilityRepository.countAvailableByTimeslot(timeslotId);
  }

  @Transactional(readOnly = true)
  public List<Event> getActiveEventsForChannel(String channelId) {
    return eventRepository.findActiveEventsByChannel(channelId);
  }

  @Transactional(readOnly = true)
  public List<Event> getEventsByChannelAndStatus(String channelId, Event.EventStatus status) {
    return eventRepository.findByChannelIdAndStatus(channelId, status);
  }

  @Transactional(readOnly = true)
  public List<Event> findDueActiveEvents(LocalDateTime now) {
    return eventRepository.findDueActiveEvents(now);
  }

  @Transactional(readOnly = true)
  public List<Availability> getUserVotesForEvent(String userDiscordId, Long eventId) {
    return availabilityRepository.findByEventAndUser(eventId, userDiscordId);
  }

  @Transactional
  public void closeEvent(Long eventId) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    event.setStatus(Event.EventStatus.CLOSED);
    eventRepository.save(event);
  }

  @Transactional
  public void updateEventDeadline(Long eventId, LocalDateTime deadline) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    event.setDeadline(deadline);
    eventRepository.save(event);
  }

  @Transactional
  public void updateUserTimezone(String userDiscordId, String timezone) {
    User user = userRepository.findById(userDiscordId)
        .orElseGet(() -> User.builder().discordId(userDiscordId).username(userDiscordId).build());
    user.setTimezone(timezone);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public String getUserTimezoneOrDefault(String userDiscordId) {
    return userRepository.findById(userDiscordId)
        .map(User::getTimezone)
        .filter(tz -> tz != null && !tz.isBlank())
        .orElse("UTC");
  }

  public record TimeslotRequest(LocalDateTime startTime, LocalDateTime endTime, String description) {}
}
