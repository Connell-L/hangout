package uk.co.louiseconnell.hangout.service.commands.text;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.hangout.service.DiscordHelpService;

@Component
public class GetHelpCommand implements TextCommand {

  private final DiscordHelpService helpService;

  public GetHelpCommand(DiscordHelpService helpService) {
    this.helpService = helpService;
  }

  @Override
  public String getName() {
    return "!help";
  }

  @Override
  public Mono<Void> handle(MessageCreateEvent event) {
    String helpText = helpService.buildHelpText();
    if (event.getMessage() == null) {
      return Mono.empty();
    }
    return event.getMessage().getChannel()
        .cast(MessageChannel.class)
        .flatMap(channel -> channel.createMessage(helpText))
        .then();
  }
}

