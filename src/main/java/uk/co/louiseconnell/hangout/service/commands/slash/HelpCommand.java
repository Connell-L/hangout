package uk.co.louiseconnell.hangout.service.commands.slash;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.hangout.service.DiscordHelpService;

import java.util.Objects;

@Component
public class HelpCommand implements SlashCommand {

  private final DiscordHelpService helpService;

  public HelpCommand(DiscordHelpService helpService) {
    this.helpService = Objects.requireNonNull(helpService);
  }

  @Override
  public String getName() {
    return "help";
  }

  @Override
  public Mono<Void> handle(ChatInputInteractionEvent event) {
    String helpText = helpService.buildHelpText();
    return event.reply()
        .withEphemeral(true)
        .withContent(helpText);
  }
}
