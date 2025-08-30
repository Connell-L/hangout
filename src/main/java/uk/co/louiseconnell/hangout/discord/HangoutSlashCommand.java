package uk.co.louiseconnell.hangout.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import reactor.core.publisher.Mono;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import uk.co.louiseconnell.hangout.entity.Availability;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.entity.Timeslot;
import uk.co.louiseconnell.hangout.service.DiscordEmbedService;
import uk.co.louiseconnell.hangout.service.HangoutService;
import uk.co.louiseconnell.hangout.service.commands.slash.SlashCommand;
import uk.co.louiseconnell.hangout.util.TimezoneUtil;

@Component
@RequiredArgsConstructor
@Slf4j
public class HangoutSlashCommand implements SlashCommand {

  private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private final HangoutService hangoutService;
  private final DiscordEmbedService embedService;
  private final GatewayDiscordClient gateway;

  @Override
  public String getName() {
    return "hangout";
  }

  /**
   * Handle /hangout command with subcommands: create, availability
   */
  public Mono<Void> handle(final ChatInputInteractionEvent event) {
    try {
      final Optional<ApplicationCommandInteractionOption> subOpt = event.getOptions().stream().findFirst();
      if (subOpt.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Please specify a subcommand: create or availability.")
            .ephemeral(true)
            .build());
      }

      final ApplicationCommandInteractionOption sub = subOpt.get();
      final String subName = sub.getName();

      return switch (subName) {
        case "menu" -> handleMenu(event);
        case "create" -> handleCreate(event, sub);
        case "availability" -> handleAvailability(event, sub);
        case "list" -> handleList(event);
        case "view" -> handleView(event, sub);
        case "close" -> handleClose(event, sub);
        case "timezone" -> handleTimezone(event, sub);
        case "draft_create" -> handleDraftCreate(event, sub);
        case "draft_propose" -> handleDraftPropose(event, sub);
        case "draft_finalize" -> handleDraftFinalize(event, sub);
        default -> event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Unknown subcommand: " + subName)
            .ephemeral(true)
            .build());
      };
    } catch (final Exception ex) {
      log.error("Error handling hangout command", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ An error occurred while processing the command. Please try again.")
          .ephemeral(true)
          .build());
    }
  }

  private Mono<java.util.List<Permission>> getMissingPermissions(Snowflake channelId, java.util.List<Permission> required) {
    return gateway.getChannelById(channelId)
        .ofType(GuildMessageChannel.class)
        .flatMap(ch -> ch.getEffectivePermissions(gateway.getSelfId()))
        .map(perms -> required.stream()
            .filter(p -> !perms.contains(p))
            .toList())
        .defaultIfEmpty(required);
  }

  private String formatMissing(java.util.List<Permission> missing) {
    if (missing == null || missing.isEmpty()) {
      return "";
    }
    return missing.stream().map(Permission::name).reduce((a,b) -> a + ", " + b).orElse("");
  }

  private Mono<Void> handleMenu(final ChatInputInteractionEvent event) {
    final var embed = discord4j.core.spec.EmbedCreateSpec.builder()
        .title("Hangout Panel")
        .description("Choose an action below\n• Create Event: Start and publish an event now\n• Create Draft: Start a draft to collect proposals\n• List: See events in this channel\n• Propose: Add a timeslot to a draft via modal")
        .build();

    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .addEmbed(embed)
        .components(ActionRow.of(
            Button.primary("hangout:action:create", "Create Event"),
            Button.secondary("hangout:action:draft", "Create Draft"),
            Button.secondary("hangout:action:list", "List Events"),
            Button.success("hangout:action:propose", "Propose Time")
        ))
        .ephemeral(true)
        .build());
  }

  private Mono<Void> handleCreate(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    final String title = getSubOptionString(sub, "title").orElse("Hangout Event");
    final String description = getSubOptionString(sub, "description").orElse(null);
    final Optional<String> deadlineStrOpt = getSubOptionString(sub, "deadline");
    LocalDateTime deadline = null;
    if (deadlineStrOpt.isPresent()) {
      try {
        deadline = LocalDateTime.parse(deadlineStrOpt.get(), DATETIME_FORMAT);
      } catch (Exception e) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Invalid deadline format. Use YYYY-MM-DD HH:MM (UTC).")
            .ephemeral(true)
            .build());
      }
      if (deadline.isBefore(LocalDateTime.now())) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Deadline must be in the future (UTC).")
            .ephemeral(true)
            .build());
      }
    }

    final List<HangoutService.TimeslotRequest> timeslots = parseTimeslots(sub);

    if (timeslots.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ You must provide at least one timeslot! Use the format: `YYYY-MM-DD HH:MM`")
          .ephemeral(true)
          .build());
    }

    // If deadline is set, ensure it's not before the latest timeslot end
    if (deadline != null) {
      LocalDateTime latestEnd = timeslots.stream()
          .map(HangoutService.TimeslotRequest::endTime)
          .max(LocalDateTime::compareTo)
          .orElse(null);
      if (latestEnd != null && deadline.isBefore(latestEnd)) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Deadline must be after the latest timeslot end (UTC).")
            .ephemeral(true)
            .build());
      }
    }

    final String creatorId = event.getInteraction().getUser().getId().asString();
    final String channelId = event.getInteraction().getChannelId().asString();

    final Event hangoutEvent = hangoutService.createHangoutEvent(title, description, creatorId, channelId, deadline, timeslots);

    final String userTimezone = hangoutService.getUserTimezoneOrDefault(creatorId);
    final var embed = embedService.createHangoutEmbed(hangoutEvent, userTimezone);

    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .addEmbed(embed)
            .components(ActionRow.of(
                Button.secondary("hangout:evt:setdeadline:" + hangoutEvent.getId(), "Set Deadline"),
                Button.danger("hangout:evt:close:" + hangoutEvent.getId(), "Close")
            ))
            .build())
        .then(event.getReply())
        .flatMap(message -> {
          hangoutService.updateEventMessageId(hangoutEvent.getId(), message.getId().asString());

          final Snowflake channelSnowflake = event.getInteraction().getChannelId();
          final List<Permission> required = java.util.List.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY, Permission.ADD_REACTIONS);
          return getMissingPermissions(channelSnowflake, required).flatMap(missing -> {
            if (!missing.isEmpty()) {
              // Send an ephemeral follow-up noting missing perms; skip reactions
              String note = "Note: bot missing permissions to add reactions: " + missing.stream().map(Permission::name).reduce((a,b)->a+", "+b).orElse("");
              return event.createFollowup(InteractionFollowupCreateSpec.builder()
                      .content(note)
                      .ephemeral(true)
                      .build())
                  .then();
            }
            final List<Mono<Void>> reactions = new ArrayList<>();
            final List<Timeslot> eventTimeslots = hangoutService.getTimeslotsByEvent(hangoutEvent.getId());
            for (Timeslot timeslot : eventTimeslots) {
              reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode(timeslot.getEmoji())));
            }
            reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❓")));
            reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❌")));
            return Mono.when(reactions).onErrorResume(err -> Mono.empty());
          });
        });
  }

  private Mono<Void> handleAvailability(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    try {
      final Long eventId = resolveEventIdArg(event, sub)
          .orElseThrow(() -> new IllegalArgumentException("Provide either event_id or message_link."));

      final var evOpt = hangoutService.getEventById(eventId);
      if (evOpt.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Event not found.")
            .ephemeral(true)
            .build());
      }
      if (evOpt.get().getStatus() == Event.EventStatus.CLOSED) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("🔒 This event is closed. Availability updates are disabled.")
            .ephemeral(true)
            .build());
      }
      final int choice = getSubOptionLong(sub, "choice").map(Long::intValue).orElseThrow(() -> new IllegalArgumentException("choice is required"));
      final String statusStr = getSubOptionString(sub, "status").orElse("AVAILABLE");
      final boolean remove = getSubOptionBoolean(sub, "remove").orElse(false);

      final List<Timeslot> slots = hangoutService.getTimeslotsByEvent(eventId);
      if (slots.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Event not found or has no timeslots.")
            .ephemeral(true)
            .build());
      }

      if (choice < 1 || choice > slots.size()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Invalid choice. Please select between 1 and " + slots.size() + ".")
            .ephemeral(true)
            .build());
      }

      final Timeslot selected = slots.get(choice - 1);
      final String userId = event.getInteraction().getUser().getId().asString();

      if (remove) {
        hangoutService.removeUserVote(userId, selected.getId());
        return postAvailabilityUpdate(event, eventId, "🗑️ Removed your vote for option " + choice + ".");
      }

      final Availability.AvailabilityStatus status;
      try {
        status = Availability.AvailabilityStatus.valueOf(statusStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Invalid status. Use AVAILABLE, MAYBE, or UNAVAILABLE.")
            .ephemeral(true)
            .build());
      }

      hangoutService.voteForTimeslot(userId, selected.getId(), status);
      final String verb = switch (status) {
        case AVAILABLE -> "✅ Set you as AVAILABLE for option ";
        case MAYBE -> "❓ Set you as MAYBE for option ";
        case UNAVAILABLE -> "🚫 Set you as UNAVAILABLE for option ";
      };
      return postAvailabilityUpdate(event, eventId, verb + choice + ".");
    } catch (Exception ex) {
      log.error("Error handling availability subcommand", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to set availability. Please check inputs and try again.")
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> postAvailabilityUpdate(ChatInputInteractionEvent event, long eventId, String message) {
    // Try to update the event embed to reflect latest counts
    try {
      final var eventOpt = hangoutService.getEventById(eventId);
      if (eventOpt.isPresent()) {
        final var ev = eventOpt.get();
        if (ev.getMessageId() != null) {
          final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
          final var channelSnowflake = Snowflake.of(ev.getChannelId());
          final var messageSnowflake = Snowflake.of(ev.getMessageId());
          gateway.getMessageById(channelSnowflake, messageSnowflake)
              .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
              .onErrorResume(err -> Mono.empty())
              .subscribe();
        }
      }
    } catch (Exception e) {
      log.warn("Could not update embed after availability change for event {}", eventId, e);
    }
    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .content(message)
        .ephemeral(true)
        .build());
  }

  private Optional<Long> resolveEventIdArg(ChatInputInteractionEvent event, ApplicationCommandInteractionOption sub) {
    // Prefer explicit event_id
    Optional<Long> idOpt = getSubOptionLong(sub, "event_id");
    if (idOpt.isPresent()) {
      return idOpt;
    }

    // Try message_link
    Optional<String> linkOpt = getSubOptionString(sub, "message_link");
    if (linkOpt.isEmpty()) {
      return Optional.empty();
    }
    String link = linkOpt.get().trim();
    // Accept either a full message URL or a bare message ID
    String messageId = null;
    try {
      if (link.matches("^https?://\\S+")) {
        String[] parts = link.split("/");
        if (parts.length >= 3) {
          messageId = parts[parts.length - 1];
        }
      } else {
        messageId = link; // assume raw snowflake
      }
      if (messageId != null && !messageId.isBlank()) {
        return hangoutService.getEventByMessageId(messageId).map(Event::getId);
      }
    } catch (Exception ignored) {
    }
    return Optional.empty();
  }

  private Mono<Void> handleList(final ChatInputInteractionEvent event) {
    final String channelId = event.getInteraction().getChannelId().asString();
    final String viewerTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
    final java.time.ZoneId viewerZone = java.time.ZoneId.of(viewerTz);
    final var events = hangoutService.getActiveEventsForChannel(channelId);
    final var drafts = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.DRAFT);

    final var closedAll = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.CLOSED);
    final var closed = closedAll.size() > 5 ? closedAll.subList(0, 5) : closedAll;

    if (events.isEmpty() && closed.isEmpty() && drafts.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("No hangout events found in this channel.")
          .ephemeral(true)
          .build());
    }

    final var builder = new StringBuilder();
    final var guildIdOpt = event.getInteraction().getGuildId().map(Snowflake::asString);

    if (!drafts.isEmpty()) {
      builder.append("Drafts:\n");
      for (Event ev : drafts) {
        builder.append("• ").append(ev.getTitle())
            .append(" (ID: ").append(ev.getId()).append(", DRAFT)");
        if (ev.getMessageId() != null && guildIdOpt.isPresent()) {
          String url = "https://discord.com/channels/" + guildIdOpt.get() + "/" + ev.getChannelId() + "/" + ev.getMessageId();
          builder.append(" — ").append(url);
        }
        builder.append("\n");
      }
      builder.append("\n");
    }

    if (!events.isEmpty()) {
      builder.append("Active events:\n");
      for (Event ev : events) {
        builder.append("• ").append(ev.getTitle())
            .append(" (ID: ").append(ev.getId()).append(", ACTIVE)");
        if (ev.getDeadline() != null) {
          builder.append(" — deadline: ")
              .append(TimezoneUtil.formatForDiscord(ev.getDeadline(), viewerZone));
        }
        if (ev.getMessageId() != null && guildIdOpt.isPresent()) {
          String url = "https://discord.com/channels/" + guildIdOpt.get() + "/" + ev.getChannelId() + "/" + ev.getMessageId();
          builder.append(" — ").append(url);
        }
        builder.append("\n");
      }
      builder.append("\n");
    }

    if (!closed.isEmpty()) {
      builder.append("Recently closed (up to 5):\n");
      for (Event ev : closed) {
        builder.append("• ").append(ev.getTitle())
            .append(" (ID: ").append(ev.getId()).append(", CLOSED)");
        if (ev.getDeadline() != null) {
          builder.append(" — deadline: ")
              .append(TimezoneUtil.formatForDiscord(ev.getDeadline(), viewerZone));
        }
        if (ev.getMessageId() != null && guildIdOpt.isPresent()) {
          String url = "https://discord.com/channels/" + guildIdOpt.get() + "/" + ev.getChannelId() + "/" + ev.getMessageId();
          builder.append(" — ").append(url);
        }
        builder.append("\n");
      }
    }

    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .content(builder.toString())
        .ephemeral(true)
        .build());
  }

  private Mono<Void> handleView(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    try {
      final Long eventId = resolveEventIdArg(event, sub)
          .orElseThrow(() -> new IllegalArgumentException("Provide either event_id or message_link."));
      final boolean isPublic = getSubOptionBoolean(sub, "public").orElse(false);

      final var evOpt = hangoutService.getEventById(eventId);
      if (evOpt.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Event not found.")
            .ephemeral(true)
            .build());
      }

      final var summary = embedService.createEventSummaryEmbed(evOpt.get());
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .addEmbed(summary)
          .ephemeral(!isPublic)
          .build());
    } catch (Exception ex) {
      log.error("Error handling view subcommand", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to view event.")
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> handleClose(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    try {
      final Long eventId = resolveEventIdArg(event, sub)
          .orElseThrow(() -> new IllegalArgumentException("Provide either event_id or message_link."));

      // Mark as closed
      hangoutService.closeEvent(eventId);

      final var evOpt = hangoutService.getEventById(eventId);
      if (evOpt.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Event not found after closing.")
            .ephemeral(true)
            .build());
      }
      final var ev = evOpt.get();

      // Build results embed
      final var summary = embedService.createEventSummaryEmbed(ev);

      // Edit original event message to show results if available
      if (ev.getMessageId() != null) {
        try {
          final var channelSnowflake = Snowflake.of(ev.getChannelId());
          final var messageSnowflake = Snowflake.of(ev.getMessageId());
          gateway.getMessageById(channelSnowflake, messageSnowflake)
              .flatMap(msg -> msg.edit(MessageEditSpec.builder()
                  .addEmbed(summary)
                  .components()
                  .build()))
              .subscribe();
        } catch (Exception e) {
          log.warn("Could not edit original message for event {}", ev.getId(), e);
        }
      }

      // Announce results (public)
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("Event closed. Here are the results:")
          .addEmbed(summary)
          .build());
    } catch (Exception ex) {
      log.error("Error handling close subcommand", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to close event.")
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> handleTimezone(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    final String userId = event.getInteraction().getUser().getId().asString();
    final Optional<String> zoneOpt = getSubOptionString(sub, "zone");
    if (zoneOpt.isEmpty() || zoneOpt.get().isBlank()) {
      final String current = hangoutService.getUserTimezoneOrDefault(userId);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("Your current timezone is: " + current + "\nSet a new one with /hangout timezone zone:<IANA id> e.g. Europe/London")
          .ephemeral(true)
          .build());
    }
    final String zone = zoneOpt.get();
    try {
      // Validate IANA timezone
      java.time.ZoneId.of(zone);
    } catch (Exception ex) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Invalid timezone. Use an IANA timezone like Europe/London or America/New_York.")
          .ephemeral(true)
          .build());
    }
    hangoutService.updateUserTimezone(userId, zone);
    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .content("✅ Timezone updated to " + zone + ". New events you create will display in your timezone.")
        .ephemeral(true)
        .build());
  }

  private Mono<Void> handleDraftCreate(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    final String title = getSubOptionString(sub, "title").orElse("Draft Event");
    final String description = getSubOptionString(sub, "description").orElse(null);
    final String creatorId = event.getInteraction().getUser().getId().asString();
    final String channelId = event.getInteraction().getChannelId().asString();

    final Event draft = hangoutService.createDraftEvent(title, description, creatorId, channelId);
    final String tz = hangoutService.getUserTimezoneOrDefault(creatorId);
    final var embed = embedService.createHangoutEmbed(draft, tz);
    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .addEmbed(embed)
        .components(java.util.List.of(ActionRow.of(
            Button.success("hangout:evt:propose:" + draft.getId(), "Propose Time"),
            Button.primary("hangout:evt:finalize:" + draft.getId(), "Finalize")
        )))
        .build())
        .then(event.getReply())
        .flatMap(message -> {
          hangoutService.updateEventMessageId(draft.getId(), message.getId().asString());
          return Mono.empty();
        });
  }

  private Mono<Void> handleDraftPropose(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    try {
      final Long eventId = resolveEventIdArg(event, sub)
          .orElseThrow(() -> new IllegalArgumentException("Provide either event_id or message_link."));
      final String startStr = getSubOptionString(sub, "start").orElseThrow();
      final String endStr = getSubOptionString(sub, "end").orElseThrow();
      final String desc = getSubOptionString(sub, "desc").orElse(null);
      final LocalDateTime start = LocalDateTime.parse(startStr, DATETIME_FORMAT);
      final LocalDateTime end = LocalDateTime.parse(endStr, DATETIME_FORMAT);
      if (end.isBefore(start)) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ End time must be after start time.")
            .ephemeral(true)
            .build());
      }

      final var evOpt = hangoutService.getEventById(eventId);
      if (evOpt.isEmpty()) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ Draft event not found.")
            .ephemeral(true)
            .build());
      }
      if (evOpt.get().getStatus() != Event.EventStatus.DRAFT) {
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("❌ This command only works for DRAFT events.")
            .ephemeral(true)
            .build());
      }

      final var t = hangoutService.addTimeslotToEvent(eventId, new HangoutService.TimeslotRequest(start, end, desc));

      // Update embed and add reaction for new emoji
      final var ev = evOpt.get();
      final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
      if (ev.getMessageId() != null) {
        final var channelSnowflake = Snowflake.of(ev.getChannelId());
        final var messageSnowflake = Snowflake.of(ev.getMessageId());
        gateway.getMessageById(channelSnowflake, messageSnowflake)
            .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
            .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode(t.getEmoji()))))
            .subscribe();
      }

      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("✅ Proposed timeslot added.")
          .ephemeral(true)
          .build());
    } catch (Exception ex) {
      log.error("Error handling draft_propose", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to propose timeslot. Check your inputs.")
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> handleDraftFinalize(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption sub) {
    try {
      final Long eventId = resolveEventIdArg(event, sub)
          .orElseThrow(() -> new IllegalArgumentException("Provide either event_id or message_link."));
      final Event ev = hangoutService.finalizeDraftToActive(eventId);

      // Build updated embed and reset reactions to only the winner + util
      final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
      if (ev.getMessageId() != null) {
        final var channelSnowflake = Snowflake.of(ev.getChannelId());
        final var messageSnowflake = Snowflake.of(ev.getMessageId());
        gateway.getMessageById(channelSnowflake, messageSnowflake)
            .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
            .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❓"))))
            .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❌"))))
            .subscribe();
      }

      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("🎯 Draft finalized. Event is now active with the winning time.")
          .ephemeral(false)
          .build());
    } catch (Exception ex) {
      log.error("Error finalizing draft", ex);
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to finalize draft: " + ex.getMessage())
          .ephemeral(true)
          .build());
    }
  }

  private List<HangoutService.TimeslotRequest> parseTimeslots(final ApplicationCommandInteractionOption sub) {
    final List<HangoutService.TimeslotRequest> timeslots = new ArrayList<>();

    for (int i = 1; i <= 5; i++) {
      final String startOption = "time" + i + "_start";
      final String endOption = "time" + i + "_end";
      final String descOption = "time" + i + "_desc";

      final String startTimeStr = getSubOptionString(sub, startOption).orElse(null);
      final String endTimeStr = getSubOptionString(sub, endOption).orElse(null);
      final String description = getSubOptionString(sub, descOption).orElse(null);

      if (startTimeStr != null && endTimeStr != null) {
        try {
          final LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATETIME_FORMAT);
          final LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATETIME_FORMAT);

          if (endTime.isBefore(startTime)) {
            log.warn("End time is before start time for timeslot {}", i);
            continue;
          }

          timeslots.add(new HangoutService.TimeslotRequest(startTime, endTime, description));
        } catch (final DateTimeParseException ex) {
          log.warn("Failed to parse datetime for timeslot {}: start={}, end={}", i, startTimeStr, endTimeStr);
        }
      }
    }

    return timeslots;
  }

  private Optional<String> getSubOptionString(ApplicationCommandInteractionOption sub, String name) {
    return sub.getOption(name)
        .flatMap(ApplicationCommandInteractionOption::getValue)
        .map(ApplicationCommandInteractionOptionValue::asString);
  }

  private Optional<Long> getSubOptionLong(ApplicationCommandInteractionOption sub, String name) {
    return sub.getOption(name)
        .flatMap(ApplicationCommandInteractionOption::getValue)
        .map(ApplicationCommandInteractionOptionValue::asLong);
  }

  private Optional<Boolean> getSubOptionBoolean(ApplicationCommandInteractionOption sub, String name) {
    return sub.getOption(name)
        .flatMap(ApplicationCommandInteractionOption::getValue)
        .map(ApplicationCommandInteractionOptionValue::asBoolean);
  }
}
