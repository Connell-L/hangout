package uk.co.louiseconnell.hangout;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.rest.RestClient;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@EnableScheduling
public class HangoutApplication {
  public static void main(final String[] args) {
    // Start Spring application
    new SpringApplicationBuilder(HangoutApplication.class).build().run(args);
  }

  @Configuration
  public static class DiscordConfiguration {
    private final String token;

    public DiscordConfiguration(final @Value("${discord.bot.token}") String token) {
      if (token == null || token.isBlank()) {
        throw new IllegalStateException(
            "Missing Discord bot token. Set environment variable DISCORD_BOT_TOKEN or property 'discord.bot.token'.");
      }
      this.token = token;
    }

    /**
     * Create a Discord client and set the initial presence to online with a listening activity.
     *
     * @return a GatewayDiscordClient instance
     */
    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
      return DiscordClientBuilder.create(token)
          .build()
          .gateway()
          .setEnabledIntents(IntentSet.of(
              Intent.GUILDS,
              Intent.GUILD_MESSAGES,
              Intent.GUILD_MESSAGE_REACTIONS
          ))
          .setInitialPresence(ignore -> ClientPresence.online(ClientActivity.listening("to /commands")))
          .login()
          .block();
    }

    @Bean
    public RestClient discordRestClient(final GatewayDiscordClient client) {
      return client.getRestClient();
    }
  }
}
