package uk.co.louiseconnell.discordbot.service.commands.text;

import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public interface TextCommand {
  String getName();
  Mono<Void> handle(MessageCreateEvent event);
}

