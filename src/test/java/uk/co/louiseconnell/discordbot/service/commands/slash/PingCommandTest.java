package uk.co.louiseconnell.discordbot.service.commands.slash;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PingCommandTest {

  private PingCommand pingCommand;
  private ChatInputInteractionEvent event;

  @BeforeEach
  void setUp() {
    pingCommand = new PingCommand();
    event = Mockito.mock(ChatInputInteractionEvent.class);
  }

  @Test
  void shouldReplyWithPong_WhenPingCommandIsInvoked() {
    final InteractionApplicationCommandCallbackReplyMono replyMono = Mockito.mock(InteractionApplicationCommandCallbackReplyMono.class);
    when(event.reply()).thenReturn(replyMono);
    when(replyMono.withEphemeral(true)).thenReturn(replyMono);
    when(replyMono.withContent(anyString())).thenReturn(replyMono);

    pingCommand.handle(event);

    verify(replyMono).withContent("Pong!");
  }

  @Test
  void shouldSetEphemeralToTrue_WhenPingCommandIsInvoked() {
    final InteractionApplicationCommandCallbackReplyMono replyMono = Mockito.mock(InteractionApplicationCommandCallbackReplyMono.class);
    when(event.reply()).thenReturn(replyMono);
    when(replyMono.withEphemeral(true)).thenReturn(replyMono);
    when(replyMono.withContent(anyString())).thenReturn(replyMono);

    pingCommand.handle(event);

    verify(replyMono).withEphemeral(true);
  }

  @Test
  void shouldReturnPing_WhenGetNameIsCalled() {
    final String name = pingCommand.getName();

    assertEquals("ping", name);
  }
}