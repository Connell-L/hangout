package uk.co.louiseconnell.discordbot.service.commands.text;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

@Component
public class GetPingCommand implements TextCommand {

  @Override
  public final String getName() {
    return "!ping";
  }

  @Override
  public Mono<Void> handle(MessageCreateEvent event) {
    final long startTime = System.currentTimeMillis();
    if (event.getMessage() == null) {
      return Mono.empty();
    }
    return event.getMessage().getChannel().cast(MessageChannel.class).flatMap(channel -> channel.createMessage("Pong!")).flatMap(message -> {
      long endTime = System.currentTimeMillis();
      long latency = endTime - startTime;
      return message.getChannel().flatMap(channel -> channel.createMessage(String.format("Pong! Latency: %dms", latency)));
    }).then();
  }
}