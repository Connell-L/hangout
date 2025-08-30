package uk.co.louiseconnell.hangout.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.hangout.entity.Event;
import uk.co.louiseconnell.hangout.service.DiscordEmbedService;
import uk.co.louiseconnell.hangout.service.HangoutService;
import discord4j.core.GatewayDiscordClient;
import discord4j.common.util.Snowflake;
import discord4j.core.spec.MessageEditSpec;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoCloseScheduler {

  private final HangoutService hangoutService;
  private final DiscordEmbedService embedService;
  private final GatewayDiscordClient gateway;

  // Run every minute
  @Scheduled(fixedDelay = 60_000)
  public void autoCloseDueEvents() {
    try {
      List<Event> due = hangoutService.findDueActiveEvents(LocalDateTime.now());
      if (due.isEmpty()) {
        return;
      }

      log.info("Auto-closing {} event(s) past deadline", due.size());
      for (Event ev : due) {
        try {
          hangoutService.closeEvent(ev.getId());
          var summary = embedService.createEventSummaryEmbed(ev);
          if (ev.getMessageId() != null) {
            var channelSnowflake = Snowflake.of(ev.getChannelId());
            var messageSnowflake = Snowflake.of(ev.getMessageId());
            gateway.getMessageById(channelSnowflake, messageSnowflake)
                .flatMap(msg -> msg.edit(MessageEditSpec.builder().addEmbed(summary).build()))
                .onErrorResume(err -> {
                  log.warn("Failed to edit message for auto-closed event {}", ev.getId(), err);
                  return Mono.empty();
                })
                .subscribe();
          }
        } catch (Exception e) {
          log.error("Failed to auto-close event {}", ev.getId(), e);
        }
      }
    } catch (Exception e) {
      log.error("Error during auto-close scheduler run", e);
    }
  }
}
