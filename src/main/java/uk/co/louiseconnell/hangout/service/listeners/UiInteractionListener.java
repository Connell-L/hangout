package uk.co.louiseconnell.hangout.service.listeners;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.service.DiscordEmbedService;
import uk.co.louiseconnell.hangout.service.HangoutService;
import uk.co.louiseconnell.hangout.util.TimezoneUtil;

@Component
@Slf4j
public class UiInteractionListener {

  private final HangoutService hangoutService;
  private final DiscordEmbedService embedService;
  private final GatewayDiscordClient gateway;

  public UiInteractionListener(final HangoutService hangoutService,
      final DiscordEmbedService embedService,
      final GatewayDiscordClient gateway) {
    this.hangoutService = hangoutService;
    this.embedService = embedService;
    this.gateway = gateway;
    this.gateway.on(ButtonInteractionEvent.class, this::handleButton).subscribe();
    this.gateway.on(SelectMenuInteractionEvent.class, this::handleSelectMenu).subscribe();
    this.gateway.on(ModalSubmitInteractionEvent.class, this::handleModalSubmit).subscribe();
  }

  private Mono<java.util.List<Permission>> getMissingPermissions(final Snowflake channelId, final java.util.List<Permission> required) {
    return gateway.getChannelById(channelId)
        .ofType(GuildMessageChannel.class)
        .flatMap(ch -> ch.getEffectivePermissions(gateway.getSelfId()))
        .map(perms -> required.stream()
            .filter(p -> !perms.contains(p))
            .toList())
        .defaultIfEmpty(required);
  }

  private String formatMissing(final java.util.List<Permission> missing) {
    if (missing == null || missing.isEmpty()) {
      return "";
    }
    return missing.stream().map(Permission::name).reduce((a, b) -> a + ", " + b).orElse("");
  }

  private String getModalValue(final ModalSubmitInteractionEvent event, final String customId) {
    for (TextInput ti : event.getComponents(TextInput.class)) {
      final String id = ti.getCustomId();
      if (customId.equals(id)) {
        final String val = ti.getValue().orElse("");
        return val;
      }
    }
    return "";
  }

  private Mono<Void> handleButton(final ButtonInteractionEvent event) {
    final String customId = event.getCustomId();
    try {
      switch (customId) {
        case "hangout:action:create": {
          return onCreate(event);
        }
        case "hangout:action:draft": {
          return onCreateDraft(event);
        }
        case "hangout:action:list": {
          return onList(event);
        }
        case "hangout:action:propose": {
          return onPropose(event);
        }
        default: {
          // continue below for prefix-based handlers
        }
      }
      if (customId.startsWith("hangout:list:page:")) {
        final int offset = Integer.parseInt(customId.substring("hangout:list:page:".length()));
        return renderListPage(event, offset);
      }
      if (customId.startsWith("hangout:evt:view:")) {
        final Long id = Long.parseLong(customId.substring("hangout:evt:view:".length()));
        final var evOpt = hangoutService.getEventById(id);
        if (evOpt.isEmpty()) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder().content("Event not found.").ephemeral(true).build());
        }
        final var summary = embedService.createEventSummaryEmbed(evOpt.get());
        return event.reply(InteractionApplicationCommandCallbackSpec.builder().addEmbed(summary).ephemeral(true).build());
      }
      if (customId.startsWith("hangout:evt:propose:")) {
        final String id = customId.substring("hangout:evt:propose:".length());
        final var modal = InteractionPresentModalSpec.builder()
            .customId("hangout:modal:propose:" + id)
            .title("Propose Timeslot")
            .addComponent(ActionRow.of(TextInput.small("start", "Start (e.g., Fri 19:00)").required(true)))
            .addComponent(ActionRow.of(TextInput.small("end", "End (e.g., Fri 21:00)").required(true)))
            .addComponent(ActionRow.of(TextInput.paragraph("desc", "Note (optional)").required(false)))
            .build();
        return event.presentModal(modal);
      }
      if (customId.startsWith("hangout:evt:setdeadline:")) {
        final String id = customId.substring("hangout:evt:setdeadline:".length());
        final var modal = InteractionPresentModalSpec.builder()
            .customId("hangout:modal:set-deadline:" + id)
            .title("Set Deadline")
            .addComponent(ActionRow.of(TextInput.small("deadline", "Deadline (e.g., Sep 10 18:00)").required(true)))
            .build();
        return event.presentModal(modal);
      }
      if (customId.startsWith("hangout:evt:close:")) {
        final String id = customId.substring("hangout:evt:close:".length());
        final var modal = InteractionPresentModalSpec.builder()
            .customId("hangout:modal:close:" + id)
            .title("Close Event")
            .addComponent(ActionRow.of(TextInput.small("confirm", "Type CLOSE to confirm").required(true)))
            .build();
        return event.presentModal(modal);
      }
      if (customId.startsWith("hangout:evt:finalize:")) {
        final Long id = Long.parseLong(customId.substring("hangout:evt:finalize:".length()));
        try {
          final var ev = hangoutService.finalizeDraftToActive(id);
          if (ev.getMessageId() != null) {
            final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
            final var channelSnowflake = Snowflake.of(ev.getChannelId());
            final var messageSnowflake = Snowflake.of(ev.getMessageId());
            final List<Permission> required = java.util.List.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY, Permission.ADD_REACTIONS);
            return getMissingPermissions(channelSnowflake, required)
                .flatMap(missing -> {
                  if (!missing.isEmpty()) {
                    final String msg = "🎯 Draft finalized. Missing permissions to update message: " + formatMissing(missing);
                    return event.reply(InteractionApplicationCommandCallbackSpec.builder().content(msg).ephemeral(true).build());
                  }
                  return gateway.getMessageById(channelSnowflake, messageSnowflake)
                      .flatMap(msg -> msg.edit(MessageEditSpec.builder()
                          .addEmbed(embed)
                          .components(ActionRow.of(
                              discord4j.core.object.component.Button.secondary("hangout:evt:setdeadline:" + ev.getId(), "Set Deadline"),
                              discord4j.core.object.component.Button.danger("hangout:evt:close:" + ev.getId(), "Close")
                          ))
                          .build()))
                      .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                          .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❓"))))
                      .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                          .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❌"))))
                      .onErrorResume(err -> {
                        log.warn("Failed to update finalized event message {} in channel {}: {}", messageSnowflake.asString(), channelSnowflake.asString(), err.toString());
                        return reactor.core.publisher.Mono.empty();
                      });
                });
          }
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("🎯 Draft finalized. Event is now active.")
              .ephemeral(true)
              .build());
        } catch (final Exception ex) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("❌ Failed to finalize draft: " + ex.getMessage())
              .ephemeral(true)
              .build());
        }
      }
      return Mono.empty();
    } catch (final Exception ex) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Error: " + ex.getMessage())
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> renderListPage(final ButtonInteractionEvent event, final int offset) {
    final String channelId = event.getInteraction().getChannelId().asString();
    final String viewerTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
    final var viewerZone = java.time.ZoneId.of(viewerTz);
    final List<Event> active = hangoutService.getActiveEventsForChannel(channelId);
    final List<Event> drafts = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.DRAFT);
    final List<Event> closedAll = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.CLOSED);
    final List<Event> all = new java.util.ArrayList<>();
    all.addAll(drafts);
    all.addAll(active);
    all.addAll(closedAll);
    if (all.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("No hangout events found in this channel.")
          .ephemeral(true)
          .build());
    }
    final int pageSize = 5;
    final int end = Math.min(offset + pageSize, all.size());
    final List<Event> items = all.subList(offset, end);
    final var guildIdOpt = event.getInteraction().getGuildId().map(s -> s.asString());

    final EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
        .title("Hangout Events")
        .color(Color.BLUE)
        .description("Events " + (offset + 1) + "–" + end + " of " + all.size());
    final java.util.List<discord4j.core.object.component.LayoutComponent> rows = new java.util.ArrayList<>();
    for (Event ev : items) {
      final String status = ev.getStatus().name();
      final String deadlineStr = ev.getDeadline() != null ? (" | deadline: " + TimezoneUtil.formatForDiscord(ev.getDeadline(), viewerZone)) : "";
      final String url = (ev.getMessageId() != null && guildIdOpt.isPresent())
          ? (" | " + "https://discord.com/channels/" + guildIdOpt.get() + "/" + ev.getChannelId() + "/" + ev.getMessageId())
          : "";
      embed.addField(ev.getTitle() + " (#" + ev.getId() + ")",
          status + deadlineStr + url,
          false);

      final List<discord4j.core.object.component.Button> buttons = new java.util.ArrayList<>();
      buttons.add(discord4j.core.object.component.Button.secondary("hangout:evt:view:" + ev.getId(), "View"));
      if (ev.getStatus() == Event.EventStatus.DRAFT) {
        buttons.add(discord4j.core.object.component.Button.success("hangout:evt:propose:" + ev.getId(), "Propose"));
        buttons.add(discord4j.core.object.component.Button.primary("hangout:evt:finalize:" + ev.getId(), "Finalize"));
      } else if (ev.getStatus() == Event.EventStatus.ACTIVE) {
        buttons.add(discord4j.core.object.component.Button.secondary("hangout:evt:setdeadline:" + ev.getId(), "Set Deadline"));
        buttons.add(discord4j.core.object.component.Button.danger("hangout:evt:close:" + ev.getId(), "Close"));
      }
      rows.add(ActionRow.of(buttons));
    }
    if (end < all.size()) {
      rows.add(ActionRow.of(discord4j.core.object.component.Button.primary("hangout:list:page:" + end, "Load More")));
    }
    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .addEmbed(embed.build())
        .components(rows.toArray(new discord4j.core.object.component.LayoutComponent[0]))
        .ephemeral(true)
        .build());
  }

  private Mono<Void> handleSelectMenu(final SelectMenuInteractionEvent event) {
    final String customId = event.getCustomId();
    if (!"hangout:select:draft".equals(customId)) {
      return Mono.empty();
    }
    final var selected = event.getValues();
    if (selected.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("Please select a draft event.")
          .ephemeral(true)
          .build());
    }
    final String eventId = selected.get(0);

    // Open a modal to collect start/end/desc
    final var modal = InteractionPresentModalSpec.builder()
        .customId("hangout:modal:propose:" + eventId)
        .title("Propose Timeslot")
        .addComponent(ActionRow.of(TextInput.small("start", "Start (YYYY-MM-DD HH:MM)").required(true)))
        .addComponent(ActionRow.of(TextInput.small("end", "End (YYYY-MM-DD HH:MM)").required(true)))
        .addComponent(ActionRow.of(TextInput.paragraph("desc", "Note (optional)").required(false)))
        .build();
    return event.presentModal(modal);
  }

  /**
   * Handles modal form submissions for proposing new hangout times.
   * Processes the form data, validates the input times, and adds a new timeslot to the event.
   *
   * @param event The ModalSubmitInteractionEvent containing the form submission data
   * @return A Mono<Void> representing the completion of the operation
   */
  private Mono<Void> handleModalSubmit(final ModalSubmitInteractionEvent event) {
    final String customId = event.getCustomId();
    try {
      if (customId.startsWith("hangout:modal:propose:")) {
        final Long eventId = Long.parseLong(customId.substring("hangout:modal:propose:".length()));
        final String startStr = getModalValue(event, "start");
        final String endStr = getModalValue(event, "end");
        String desc = getModalValue(event, "desc");
        if (desc != null && desc.isBlank()) {
          desc = null;
        }

        if (startStr.isBlank() || endStr.isBlank()) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("Start and end are required.")
              .ephemeral(true)
              .build());
        }

        final String userTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
        final java.time.ZoneId zone = java.time.ZoneId.of(userTz);
        final java.time.LocalDateTime start = uk.co.louiseconnell.hangout.util.DateTimeParser.parseToUtc(startStr, zone);
        final java.time.LocalDateTime end = uk.co.louiseconnell.hangout.util.DateTimeParser.parseToUtc(endStr, zone);
        if (end.isBefore(start)) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("End time must be after start time.")
              .ephemeral(true)
              .build());
        }

        final var timeslot = hangoutService.addTimeslotToEvent(eventId, new uk.co.louiseconnell.hangout.service.HangoutService.TimeslotRequest(start, end, desc));

        // Update the draft embed and add the new emoji reaction
        final var evOpt = hangoutService.getEventById(eventId);
        if (evOpt.isPresent() && evOpt.get().getMessageId() != null) {
          final var ev = evOpt.get();
          final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
          final var channelSnowflake = Snowflake.of(ev.getChannelId());
          final var messageSnowflake = Snowflake.of(ev.getMessageId());
          return gateway.getMessageById(channelSnowflake, messageSnowflake)
              .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
              .then(gateway.getMessageById(channelSnowflake, messageSnowflake)
                  .flatMap(msg -> msg.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode(timeslot.getEmoji()))))
              .then(event.reply(InteractionApplicationCommandCallbackSpec.builder()
                  .content("✅ Proposed timeslot added to draft " + eventId + ".")
                  .ephemeral(true)
                  .build()))
              .onErrorResume(err -> {
                log.warn("Failed to update draft message {} in channel {}: {}", messageSnowflake.asString(), channelSnowflake.asString(), err.toString());
                return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                    .content("✅ Proposed timeslot added to draft " + eventId + ". (Couldn’t update message; check bot permissions)")
                    .ephemeral(true)
                    .build());
              });
        }

        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("✅ Proposed timeslot added to draft " + eventId + ".")
            .ephemeral(true)
            .build());
      } else if (customId.equals("hangout:modal:create")) {
        String title = getModalValue(event, "title");
        if (title == null || title.isBlank()) {
          title = "Hangout Event";
        }
        String description = getModalValue(event, "description");
        if (description != null && description.isBlank()) {
          description = null;
        }
        final String startStr = getModalValue(event, "time1_start");
        final String endStr = getModalValue(event, "time1_end");

        if (title.isBlank() || startStr.isBlank() || endStr.isBlank()) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("Title, start and end are required.")
              .ephemeral(true)
              .build());
        }

        final String userTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
        final java.time.ZoneId zone = java.time.ZoneId.of(userTz);
        final java.time.LocalDateTime start = uk.co.louiseconnell.hangout.util.DateTimeParser.parseToUtc(startStr, zone);
        final java.time.LocalDateTime end = uk.co.louiseconnell.hangout.util.DateTimeParser.parseToUtc(endStr, zone);
        if (end.isBefore(start)) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("End time must be after start time.")
              .ephemeral(true)
              .build());
        }

        final String creatorId = event.getInteraction().getUser().getId().asString();
        final String channelId = event.getInteraction().getChannelId().asString();
        final java.util.List<uk.co.louiseconnell.hangout.service.HangoutService.TimeslotRequest> slots = java.util.List.of(
            new uk.co.louiseconnell.hangout.service.HangoutService.TimeslotRequest(start, end, null));
        final var hangoutEvent = hangoutService.createHangoutEvent(title, description, creatorId, channelId, null, slots);
        final String tz = hangoutService.getUserTimezoneOrDefault(creatorId);
        final var embed = embedService.createHangoutEmbed(hangoutEvent, tz);

        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .addEmbed(embed)
            .build())
            .then(event.getReply())
            .flatMap(message -> {
              hangoutService.updateEventMessageId(hangoutEvent.getId(), message.getId().asString());
              final java.util.List<reactor.core.publisher.Mono<Void>> reactions = new java.util.ArrayList<>();
              final java.util.List<uk.co.louiseconnell.hangout.entity.Timeslot> eventTimeslots = hangoutService.getTimeslotsByEvent(hangoutEvent.getId());
              for (uk.co.louiseconnell.hangout.entity.Timeslot t : eventTimeslots) {
                reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode(t.getEmoji())));
              }
              reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❓")));
              reactions.add(message.addReaction(discord4j.core.object.reaction.ReactionEmoji.unicode("❌")));
              return reactor.core.publisher.Mono.when(reactions);
            });
      } else if (customId.equals("hangout:modal:create-draft")) {
        String title = getModalValue(event, "title");
        if (title == null || title.isBlank()) {
          title = "Draft Event";
        }
        String description = getModalValue(event, "description");
        if (description != null && description.isBlank()) {
          description = null;
        }
        final String creatorId = event.getInteraction().getUser().getId().asString();
        final String channelId = event.getInteraction().getChannelId().asString();
        final var draft = hangoutService.createDraftEvent(title, description, creatorId, channelId);

        final String tz = hangoutService.getUserTimezoneOrDefault(creatorId);
        final var embed = embedService.createHangoutEmbed(draft, tz);
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .addEmbed(embed)
            .components(java.util.List.of(ActionRow.of(
                discord4j.core.object.component.Button.success("hangout:evt:propose:" + draft.getId(), "Propose Time"),
                discord4j.core.object.component.Button.primary("hangout:evt:finalize:" + draft.getId(), "Finalize"))))
            .build())
            .then(event.getReply())
            .flatMap(message -> {
              hangoutService.updateEventMessageId(draft.getId(), message.getId().asString());
              return Mono.empty();
            });
      } else if (customId.startsWith("hangout:modal:set-deadline:")) {
        final Long eventId = Long.parseLong(customId.substring("hangout:modal:set-deadline:".length()));
        final String input = getModalValue(event, "deadline");
        final String userTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
        final java.time.ZoneId zone = java.time.ZoneId.of(userTz);
        final java.time.LocalDateTime deadline = uk.co.louiseconnell.hangout.util.DateTimeParser.parseToUtc(input, zone);

        if (deadline.isBefore(java.time.LocalDateTime.now())) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("Deadline must be in the future.")
              .ephemeral(true)
              .build());
        }
        // Validate after latest timeslot end (if any)
        final var slots = hangoutService.getTimeslotsByEvent(eventId);
        if (!slots.isEmpty()) {
          final var latestEnd = slots.stream().map(uk.co.louiseconnell.hangout.entity.Timeslot::getEndTime).max(java.time.LocalDateTime::compareTo).get();
          if (deadline.isBefore(latestEnd)) {
            return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                .content("Deadline must be after the latest timeslot end.")
                .ephemeral(true)
                .build());
          }
        }

        hangoutService.updateEventDeadline(eventId, deadline);
        // Update message embed
        final var evOpt = hangoutService.getEventById(eventId);
        if (evOpt.isPresent() && evOpt.get().getMessageId() != null) {
          final var ev = evOpt.get();
          final var embed = embedService.createHangoutEmbed(ev, hangoutService.getUserTimezoneOrDefault(ev.getCreatorDiscordId()));
          final var channelSnowflake = Snowflake.of(ev.getChannelId());
          final var messageSnowflake = Snowflake.of(ev.getMessageId());
          gateway.getMessageById(channelSnowflake, messageSnowflake)
              .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(embed).build()))
              .onErrorResume(err -> {
                log.warn("Failed to edit message {} in channel {} after deadline update: {}", messageSnowflake.asString(), channelSnowflake.asString(), err.toString());
                return reactor.core.publisher.Mono.empty();
              })
              .subscribe();
        }
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("✅ Deadline updated.")
            .ephemeral(true)
            .build());
      } else if (customId.startsWith("hangout:modal:close:")) {
        final Long eventId = Long.parseLong(customId.substring("hangout:modal:close:".length()));
        final String confirm = getModalValue(event, "confirm");
        if (!"CLOSE".equalsIgnoreCase(confirm.trim())) {
          return event.reply(InteractionApplicationCommandCallbackSpec.builder()
              .content("Type CLOSE to confirm.")
              .ephemeral(true)
              .build());
        }
        hangoutService.closeEvent(eventId);
        final var evOpt = hangoutService.getEventById(eventId);
        if (evOpt.isPresent() && evOpt.get().getMessageId() != null) {
          final var ev = evOpt.get();
          final var summary = embedService.createEventSummaryEmbed(ev);
          final var channelSnowflake = Snowflake.of(ev.getChannelId());
          final var messageSnowflake = Snowflake.of(ev.getMessageId());
          gateway.getMessageById(channelSnowflake, messageSnowflake)
              .flatMap(msg -> msg.edit(MessageEditSpec.builder()
                  .addEmbed(summary)
                  .components()
                  .build()))
              .onErrorResume(err -> {
                log.warn("Failed to edit message {} in channel {} after close: {}", messageSnowflake.asString(), channelSnowflake.asString(), err.toString());
                return reactor.core.publisher.Mono.empty();
              })
              .subscribe();
        }
        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
            .content("🔒 Event closed.")
            .ephemeral(true)
            .build());
      }
      return Mono.empty();
    } catch (final Exception ex) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("❌ Failed to handle modal: " + ex.getMessage())
          .ephemeral(true)
          .build());
    }
  }

  private Mono<Void> onCreate(final ButtonInteractionEvent event) {
    final var modal = InteractionPresentModalSpec.builder()
        .customId("hangout:modal:create")
        .title("Create Hangout")
        .addComponent(ActionRow.of(TextInput.small("title", "Title").required(true)))
        .addComponent(ActionRow.of(TextInput.paragraph("description", "Description (optional)").required(false)))
        .addComponent(ActionRow.of(TextInput.small("time1_start", "Start (YYYY-MM-DD HH:MM)").required(true)))
        .addComponent(ActionRow.of(TextInput.small("time1_end", "End (YYYY-MM-DD HH:MM)").required(true)))
        .build();
    return event.presentModal(modal);
  }

  private Mono<Void> onCreateDraft(final ButtonInteractionEvent event) {
    final var modal = InteractionPresentModalSpec.builder()
        .customId("hangout:modal:create-draft")
        .title("Create Draft")
        .addComponent(ActionRow.of(TextInput.small("title", "Title").required(true)))
        .addComponent(ActionRow.of(TextInput.paragraph("description", "Description (optional)").required(false)))
        .build();
    return event.presentModal(modal);
  }

  private Mono<Void> onList(final ButtonInteractionEvent event) {
    final String channelId = event.getInteraction().getChannelId().asString();
    final String viewerTz = hangoutService.getUserTimezoneOrDefault(event.getInteraction().getUser().getId().asString());
    final var viewerZone = java.time.ZoneId.of(viewerTz);
    final List<Event> active = hangoutService.getActiveEventsForChannel(channelId);
    final List<Event> drafts = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.DRAFT);
    final List<Event> closedAll = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.CLOSED);
    final List<Event> closed = closedAll.size() > 5 ? closedAll.subList(0, 5) : closedAll;

    if (active.isEmpty() && drafts.isEmpty() && closed.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("No hangout events found in this channel.")
          .ephemeral(true)
          .build());
    }

    // Build a compact embed with up to 5 entries and per-item action buttons
    final var guildIdOpt = event.getInteraction().getGuildId().map(s -> s.asString());
    final List<Event> items = new java.util.ArrayList<>();
    items.addAll(drafts);
    items.addAll(active);
    if (items.size() > 5) {
      items.subList(5, items.size()).clear();
    }

    final EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
        .title("Hangout Events")
        .color(Color.BLUE)
        .description("Select an action below each listed event. Showing up to 5.");

    final java.util.List<discord4j.core.object.component.LayoutComponent> rows = new java.util.ArrayList<>();
    for (Event ev : items) {
      final String status = ev.getStatus().name();
      final String deadlineStr = ev.getDeadline() != null ? (" | deadline: " + TimezoneUtil.formatForDiscord(ev.getDeadline(), viewerZone)) : "";
      final String url = (ev.getMessageId() != null && guildIdOpt.isPresent())
          ? (" | " + "https://discord.com/channels/" + guildIdOpt.get() + "/" + ev.getChannelId() + "/" + ev.getMessageId())
          : "";
      embed.addField(ev.getTitle() + " (#" + ev.getId() + ")",
          status + deadlineStr + url,
          false);

      final List<discord4j.core.object.component.Button> buttons = new java.util.ArrayList<>();
      buttons.add(discord4j.core.object.component.Button.secondary("hangout:evt:view:" + ev.getId(), "View"));
      if (ev.getStatus() == Event.EventStatus.DRAFT) {
        buttons.add(discord4j.core.object.component.Button.success("hangout:evt:propose:" + ev.getId(), "Propose"));
        buttons.add(discord4j.core.object.component.Button.primary("hangout:evt:finalize:" + ev.getId(), "Finalize"));
      }
      rows.add(ActionRow.of(buttons));
    }

    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .addEmbed(embed.build())
        .components(rows.toArray(new discord4j.core.object.component.LayoutComponent[0]))
        .ephemeral(true)
        .build());
  }

  private Mono<Void> onPropose(final ButtonInteractionEvent event) {
    final String channelId = event.getInteraction().getChannelId().asString();
    final List<Event> drafts = hangoutService.getEventsByChannelAndStatus(channelId, Event.EventStatus.DRAFT);
    if (drafts.isEmpty()) {
      return event.reply(InteractionApplicationCommandCallbackSpec.builder()
          .content("No draft events here. Use /hangout draft_create to start one.")
          .ephemeral(true)
          .build());
    }
    // Build a string select menu of drafts
    final List<SelectMenu.Option> options = drafts.stream()
        .limit(25)
        .map(ev -> SelectMenu.Option.of(ev.getTitle() + " (" + ev.getId() + ")", String.valueOf(ev.getId())))
        .toList();

    return event.reply(InteractionApplicationCommandCallbackSpec.builder()
        .content("Pick a draft to propose a time for:")
        .components(ActionRow.of(SelectMenu.of("hangout:select:draft", options)))
        .ephemeral(true)
        .build());
  }
}
