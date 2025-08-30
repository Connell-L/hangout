package uk.co.louiseconnell.discordbot.service.commands.slash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class GreetCommandTest {

  private GreetCommand greetCommand;
  private ChatInputInteractionEvent event;
  private ApplicationCommandInteractionOption option;
  private ApplicationCommandInteractionOptionValue value;

  @BeforeEach
  void setUp() {
    greetCommand = new GreetCommand();
    event = mock(ChatInputInteractionEvent.class);
    option = mock(ApplicationCommandInteractionOption.class);
    value = mock(ApplicationCommandInteractionOptionValue.class);
  }

  @Test
  void shouldReplyWithGreeting_WhenNameOptionIsPresent() {
    when(event.getOption("name")).thenReturn(Optional.of(option));
    when(option.getValue()).thenReturn(Optional.of(value));
    when(value.asString()).thenReturn("TestUser");

    final InteractionApplicationCommandCallbackReplyMono replyMono = mock(InteractionApplicationCommandCallbackReplyMono.class);

    when(event.reply()).thenReturn(replyMono);
    when(replyMono.withEphemeral(true)).thenReturn(replyMono);
    when(replyMono.withContent(anyString())).thenReturn(replyMono);

    greetCommand.handle(event);

    verify(replyMono).withContent("Hello, TestUser");
  }

  @Test
  void shouldThrowException_WhenNameOptionIsNotPresent() {
    when(event.getOption("name")).thenReturn(Optional.of(option));
    when(option.getValue()).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> greetCommand.handle(event));
  }

  @Test
  void shouldReturnGreet_WhenGetNameIsCalled() {
    final String name = greetCommand.getName();

    assertEquals("greet", name);
  }
}