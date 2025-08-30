package uk.co.louiseconnell.discordbot.service.listeners;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import uk.co.louiseconnell.discordbot.service.commands.text.TextCommand;

import java.util.Collection;
import java.util.List;

@Component
public class TextCommandListener {

  private final Collection<TextCommand> commands;

  public TextCommandListener(List<TextCommand> textCommands, GatewayDiscordClient client) {
    this.commands = textCommands;

    client.on(MessageCreateEvent.class, this::handle).subscribe();
  }

  public Mono<Void> handle(MessageCreateEvent event) {
    //Convert our list to a flux that we can iterate through
    return Flux.fromIterable(commands)
        //Filter out all commands that don't match the name this event is for
        .filter(command -> event.getMessage().getContent().startsWith(command.getName()))
        //Get the first (and only) item in the flux that matches our filter
        .next()
        //Have our command class handle all logic related to its specific command.
        .flatMap(command -> command.handle(event));
  }
}

