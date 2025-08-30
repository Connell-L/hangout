package uk.co.louiseconnell.hangout.discord;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.hangout.entity.Availability;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.entity.Timeslot;
import uk.co.louiseconnell.hangout.service.DiscordEmbedService;
import uk.co.louiseconnell.hangout.service.HangoutService;

import java.util.Optional;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.MessageEditSpec;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactionListener {
    
    private final HangoutService hangoutService;
    private final DiscordEmbedService embedService;
    private final GatewayDiscordClient gateway;
    
    /**
     * Handle reaction add events
     */
    public Mono<Void> handleReactionAdd(ReactionAddEvent event) {
        return event.getUser()
                .filter(user -> !user.isBot()) // Ignore bot reactions
                .flatMap(user -> {
                    String messageId = event.getMessageId().asString();
                    String userId = user.getId().asString();
                    String emoji = getEmojiString(event.getEmoji());
                    
                    return processVote(messageId, userId, emoji, true);
                })
                .onErrorResume(error -> {
                    log.error("Error handling reaction add", error);
                    return Mono.empty();
                });
    }
    
    /**
     * Handle reaction remove events
     */
    public Mono<Void> handleReactionRemove(ReactionRemoveEvent event) {
        return event.getUser()
                .filter(user -> !user.isBot()) // Ignore bot reactions
                .flatMap(user -> {
                    String messageId = event.getMessageId().asString();
                    String userId = user.getId().asString();
                    String emoji = getEmojiString(event.getEmoji());
                    
                    return processVote(messageId, userId, emoji, false);
                })
                .onErrorResume(error -> {
                    log.error("Error handling reaction remove", error);
                    return Mono.empty();
                });
    }
    
    private Mono<Void> processVote(String messageId, String userId, String emoji, boolean isAdd) {
        try {
            // Find event by message ID
            Optional<Event> eventOpt = hangoutService.getEventByMessageId(messageId);
            if (eventOpt.isEmpty()) {
                return Mono.empty(); // Not a hangout event message
            }
            
            Event event = eventOpt.get();
            
            // Ignore votes for closed events
            if (event.getStatus() == Event.EventStatus.CLOSED) {
                return Mono.empty();
            }
            
            // Handle special emojis
            if ("❌".equals(emoji)) {
                if (isAdd) {
                    // Remove all votes for this user on this event
                    removeAllUserVotes(userId, event.getId());
                }
                return Mono.empty();
            }
            
            if ("❓".equals(emoji)) {
                // Handle "maybe" votes - could be implemented later
                return Mono.empty();
            }
            
            // Find timeslot by emoji
            Optional<Timeslot> timeslotOpt = hangoutService.findTimeslotByEmoji(event.getId(), emoji);
            if (timeslotOpt.isEmpty()) {
                return Mono.empty(); // Not a valid timeslot emoji
            }
            
            Timeslot timeslot = timeslotOpt.get();
            
            if (isAdd) {
                // Add vote
                hangoutService.voteForTimeslot(userId, timeslot.getId(), Availability.AvailabilityStatus.AVAILABLE);
                log.info("User {} voted for timeslot {} ({})", userId, timeslot.getId(), emoji);
            } else {
                // Remove vote
                removeUserVote(userId, timeslot.getId());
                log.info("User {} removed vote for timeslot {} ({})", userId, timeslot.getId(), emoji);
            }
            
            // Update the embed to reflect new vote counts
            return updateEventEmbed(event, messageId);
            
        } catch (Exception e) {
            log.error("Error processing vote", e);
            return Mono.empty();
        }
    }
    
    private void removeUserVote(String userId, Long timeslotId) {
        hangoutService.removeUserVote(userId, timeslotId);
    }
    
    private void removeAllUserVotes(String userId, Long eventId) {
        hangoutService.removeAllUserVotes(userId, eventId);
    }
    
    private Mono<Void> updateEventEmbed(Event event, String messageId) {
        try {
            var embed = embedService.createHangoutEmbed(event, hangoutService.getUserTimezoneOrDefault(event.getCreatorDiscordId()));
            var channelSnowflake = Snowflake.of(event.getChannelId());
            var messageSnowflake = Snowflake.of(messageId);
            return gateway
                    .getMessageById(channelSnowflake, messageSnowflake)
                    .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
                    .onErrorResume(err -> {
                        log.warn("Failed to update event embed message {} in channel {}: {}", messageSnowflake.asString(), channelSnowflake.asString(), err.toString());
                        return Mono.empty();
                    })
                    .then();
        } catch (Exception e) {
            log.error("Failed to update event embed for event {}", event.getId(), e);
            return Mono.empty();
        }
    }
    
    private String getEmojiString(ReactionEmoji emoji) {
        if (emoji.asUnicodeEmoji().isPresent()) {
            return emoji.asUnicodeEmoji().get().getRaw();
        }
        return emoji.asCustomEmoji().map(custom -> custom.getName()).orElse("");
    }
}
