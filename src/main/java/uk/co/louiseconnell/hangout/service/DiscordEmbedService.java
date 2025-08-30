package uk.co.louiseconnell.hangout.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.entity.Timeslot;
import uk.co.louiseconnell.hangout.util.TimezoneUtil;

@Service
@RequiredArgsConstructor
public class DiscordEmbedService {

  private final HangoutService hangoutService;

  /**
   * Create embed for hangout availability planner
   */
  public EmbedCreateSpec createHangoutEmbed(final Event event, final String userTimezone) {
    final List<Timeslot> timeslots = hangoutService.getTimeslotsByEvent(event.getId());
    final ZoneId timezone = userTimezone != null ? ZoneId.of(userTimezone) : ZoneId.of("UTC");

    final boolean isDraft = event.getStatus() == Event.EventStatus.DRAFT;
    final String defaultDesc = isDraft
        ? "Use /hangout draft_propose to add options, then react to vote"
        : "React with the numbers below to vote for your availability!";

    final EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
        .color(isDraft ? Color.of(255, 165, 0) : Color.BLUE)
        .title((isDraft ? "📝 Draft: " : "🗓️ ") + event.getTitle())
        .description(event.getDescription() != null ? event.getDescription() : defaultDesc)
        .footer("Event ID: " + event.getId() + " | TZ: " + timezone.getId() + " | React to vote!", null);

    if (event.getDeadline() != null) {
      final String deadlineStr = TimezoneUtil.formatForDiscord(event.getDeadline(), timezone);
      embedBuilder.addField("⏰ Voting Deadline", deadlineStr + " (auto-close)", false);
    }

    // Add timeslot fields
    for (Timeslot timeslot : timeslots) {
      final int availableCount = hangoutService.getAvailabilityCount(timeslot.getId());
      final String timeRange = TimezoneUtil.formatTimeRange(timeslot.getStartTime(), timeslot.getEndTime(), timezone);

      final String fieldValue = String.format("**Time:** %s\n**Available:** %d people\n%s",
          timeRange,
          availableCount,
          timeslot.getDescription() != null ? "**Note:** " + timeslot.getDescription() : "");

      embedBuilder.addField(timeslot.getEmoji() + " Option " + (timeslots.indexOf(timeslot) + 1),
          fieldValue, false);
    }

    // Add instructions
    if (isDraft) {
      embedBuilder.addField("📋 Draft Instructions",
          "Use `/hangout draft_propose` to add time options, then react to vote.\n" +
              "React with number emoji for times you're **available**.\n" +
              "Use ❌ to **remove** your vote.\n" +
              "When ready, run `/hangout draft_finalize`.",
          false);
    } else {
      embedBuilder.addField("📋 How to Vote",
          "React with the number emoji for times you're **available**\n" +
              "Use ❓ if you're **maybe** available\n" +
              "Use ❌ to **remove** your vote",
          false);
    }

    return embedBuilder.build();
  }

  /**
   * Create embed for event summary/results
   */
  public EmbedCreateSpec createEventSummaryEmbed(final Event event) {
    final List<Timeslot> timeslots = hangoutService.getTimeslotsByEvent(event.getId());
    final java.time.ZoneId timezone = java.time.ZoneId.of(hangoutService.getUserTimezoneOrDefault(event.getCreatorDiscordId()));

    // Find the most popular timeslot
    final Timeslot mostPopular = timeslots.stream()
        .max((t1, t2) -> Integer.compare(
            hangoutService.getAvailabilityCount(t1.getId()),
            hangoutService.getAvailabilityCount(t2.getId())))
        .orElse(null);

    final Color summaryColor = (event.getStatus() == Event.EventStatus.CLOSED)
        ? Color.of(100, 100, 100) // gray for closed
        : Color.GREEN;            // green for active/in-progress

    final EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
        .color(summaryColor)
        .title("📊 " + event.getTitle() + " - Results")
        .description("Here are the voting results for this hangout:");

    if (event.getDeadline() != null) {
      final String deadlineStr = TimezoneUtil.formatForDiscord(event.getDeadline(), timezone);
      embedBuilder.addField("⏰ Voting Deadline", deadlineStr + " (event is now closed)", false);
    }

    // Add results for each timeslot
    for (Timeslot timeslot : timeslots) {
      final int availableCount = hangoutService.getAvailabilityCount(timeslot.getId());
      final String timeRange = TimezoneUtil.formatTimeRange(timeslot.getStartTime(), timeslot.getEndTime(), timezone);

      final String indicator = (timeslot.equals(mostPopular) && availableCount > 0) ? "🏆 " : "";
      final String fieldValue = String.format("%s**%d people available**\n%s",
          indicator, availableCount, timeRange);

      embedBuilder.addField(timeslot.getEmoji() + " Option " + (timeslots.indexOf(timeslot) + 1),
          fieldValue, true);
    }

    if (mostPopular != null && hangoutService.getAvailabilityCount(mostPopular.getId()) > 0) {
      final String winnerTime = TimezoneUtil.formatTimeRange(mostPopular.getStartTime(), mostPopular.getEndTime(), timezone);
      embedBuilder.addField("🎉 Most Popular Time",
          String.format("%s\n**%d people** can make it!",
              winnerTime,
              hangoutService.getAvailabilityCount(mostPopular.getId())),
          false);
    }

    return embedBuilder.build();
  }

  /**
   * Create embed for user's personal availability view
   */
  public EmbedCreateSpec createPersonalAvailabilityEmbed(final Event event, final String userDiscordId, final String userTimezone) {
    final List<Timeslot> timeslots = hangoutService.getTimeslotsByEvent(event.getId());
    final ZoneId timezone = userTimezone != null ? ZoneId.of(userTimezone) : ZoneId.of("UTC");

    final EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
        .color(Color.CYAN)
        .title("📅 Your Availability - " + event.getTitle())
        .description("Here's your current availability for this event:");

    // Show user's votes for each timeslot
    for (Timeslot timeslot : timeslots) {
      final String timeRange = TimezoneUtil.formatTimeRange(timeslot.getStartTime(), timeslot.getEndTime(), timezone);
      // Note: You'd need to implement getting user's specific vote status
      final String status = "Not voted"; // This would be replaced with actual vote status

      embedBuilder.addField(timeslot.getEmoji() + " Option " + (timeslots.indexOf(timeslot) + 1),
          String.format("**%s**\n%s\n**Your vote:** %s",
              timeRange,
              timeslot.getDescription() != null ? timeslot.getDescription() : "",
              status),
          false);
    }

    return embedBuilder.build();
  }
}
