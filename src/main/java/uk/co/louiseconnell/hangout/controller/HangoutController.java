package uk.co.louiseconnell.hangout.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.entity.Timeslot;
import uk.co.louiseconnell.hangout.entity.Availability;
import uk.co.louiseconnell.hangout.service.HangoutService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/hangout")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HangoutController {
    
    private final HangoutService hangoutService;
    
    /**
     * Get active events for a channel
     */
    @GetMapping("/events/channel/{channelId}")
    public ResponseEntity<List<Event>> getActiveEventsForChannel(@PathVariable String channelId) {
        List<Event> events = hangoutService.getActiveEventsForChannel(channelId);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get event by ID
     */
    @GetMapping("/events/{eventId}")
    public ResponseEntity<Event> getEvent(@PathVariable Long eventId) {
        // This would need to be implemented in the service
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Get timeslots for an event
     */
    @GetMapping("/events/{eventId}/timeslots")
    public ResponseEntity<List<Timeslot>> getEventTimeslots(@PathVariable Long eventId) {
        List<Timeslot> timeslots = hangoutService.getTimeslotsByEvent(eventId);
        return ResponseEntity.ok(timeslots);
    }
    
    /**
     * Get availability count for a timeslot
     */
    @GetMapping("/timeslots/{timeslotId}/availability/count")
    public ResponseEntity<Integer> getAvailabilityCount(@PathVariable Long timeslotId) {
        int count = hangoutService.getAvailabilityCount(timeslotId);
        return ResponseEntity.ok(count);
    }
    
    /**
     * Get user's votes for an event
     */
    @GetMapping("/events/{eventId}/users/{userDiscordId}/votes")
    public ResponseEntity<List<Availability>> getUserVotes(
            @PathVariable Long eventId, 
            @PathVariable String userDiscordId) {
        List<Availability> votes = hangoutService.getUserVotesForEvent(userDiscordId, eventId);
        return ResponseEntity.ok(votes);
    }
    
    /**
     * Vote for a timeslot
     */
    @PostMapping("/timeslots/{timeslotId}/vote")
    public ResponseEntity<Void> voteForTimeslot(
            @PathVariable Long timeslotId,
            @RequestParam String userDiscordId,
            @RequestParam Availability.AvailabilityStatus status) {
        try {
            hangoutService.voteForTimeslot(userDiscordId, timeslotId, status);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Remove user's vote for a timeslot
     */
    @DeleteMapping("/timeslots/{timeslotId}/vote")
    public ResponseEntity<Void> removeVote(
            @PathVariable Long timeslotId,
            @RequestParam String userDiscordId) {
        hangoutService.removeUserVote(userDiscordId, timeslotId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Close an event
     */
    @PutMapping("/events/{eventId}/close")
    public ResponseEntity<Void> closeEvent(@PathVariable Long eventId) {
        try {
            hangoutService.closeEvent(eventId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Update user timezone
     */
    @PutMapping("/users/{userDiscordId}/timezone")
    public ResponseEntity<Void> updateUserTimezone(
            @PathVariable String userDiscordId,
            @RequestParam String timezone) {
        hangoutService.updateUserTimezone(userDiscordId, timezone);
        return ResponseEntity.ok().build();
    }
}
