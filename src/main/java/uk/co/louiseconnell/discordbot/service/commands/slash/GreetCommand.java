package uk.co.louiseconnell.discordbot.service.commands.slash;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GreetCommand implements SlashCommand {
  @Override
  final public String getName() {
    return "greet";
  }

  @Override
  public Mono<Void> handle(ChatInputInteractionEvent event) {
    final Optional<String> nameOptional = event.getOption("name").flatMap(ApplicationCommandInteractionOption::getValue).map(ApplicationCommandInteractionOptionValue::asString);

    final String name = nameOptional.orElse("");

    if (name.isEmpty()) {
      throw new IllegalArgumentException("Name option is required");
    }

    // Reply to the slash command, with the name the user supplied
    return event.reply().withEphemeral(true).withContent("Hello, " + name);
  }
}
