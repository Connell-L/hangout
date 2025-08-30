package uk.co.louiseconnell.discordbot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.rest.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class DiscordBotApplication {
  public static void main(String[] args) {
    // Start Spring application
    new SpringApplicationBuilder(DiscordBotApplication.class).build().run(args);
  }

  @Configuration
  public static class DiscordConfiguration {
    private final String token;

    public DiscordConfiguration(@Value("${token}") String token) {
      this.token = token;
    }

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
      return DiscordClientBuilder.create(token)
          .build()
          .gateway()
          .setInitialPresence(ignore -> ClientPresence.online(ClientActivity.listening("to /commands")))
          .login()
          .block();
    }

    @Bean
    public RestClient discordRestClient(GatewayDiscordClient client) {
      return client.getRestClient();
    }
  }
}
