package uk.co.louiseconnell.hangout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;

@Component
public class GlobalCommandRegistrar implements ApplicationRunner {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final RestClient client;
  private final Environment env;

  // Use the rest client provided by our Bean
  public GlobalCommandRegistrar(final RestClient client, final Environment env) {
    this.client = client;
    this.env = env;
  }

  //This method will run only once on each start up and is automatically called with Spring so blocking is okay.
  @Override
  public void run(final ApplicationArguments args) throws IOException {
    //Create an ObjectMapper that supported Discord4J classes
    final JacksonResources d4jMapper = JacksonResources.create();

    // Convenience variables for the sake of easier to read code below.
    final PathMatchingResourcePatternResolver matcher = new PathMatchingResourcePatternResolver();
    final ApplicationService applicationService = client.getApplicationService();
    final long applicationId = client.getApplicationId().block();

    //Get our commands json from resources as command data
    final List<ApplicationCommandRequest> commands = new ArrayList<>();
    for (Resource resource : matcher.getResources("commands/slash/*.json")) {
      final ApplicationCommandRequest request = d4jMapper.getObjectMapper()
          .readValue(resource.getInputStream(), ApplicationCommandRequest.class);

      commands.add(request);
    }

    // Prefer guild-scoped registration for faster propagation if a guild ID is configured
    Optional<Long> guildIdOpt = readGuildId();
    if (guildIdOpt.isPresent()) {
      long guildId = guildIdOpt.get();
      applicationService.bulkOverwriteGuildApplicationCommand(applicationId, guildId, commands)
          .doOnNext(ignore -> logger.debug("Successfully registered Guild Commands for guild {}", guildId))
          .doOnError(e -> logger.error("Failed to register guild commands", e))
          .subscribe();
    } else {
      // Fallback to global registration if no guild is configured
      applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, commands)
          .doOnNext(ignore -> logger.debug("Successfully registered Global Commands"))
          .doOnError(e -> logger.error("Failed to register global commands", e))
          .subscribe();
    }
  }

  private Optional<Long> readGuildId() {
    try {
      String guildStr = env.getProperty("discord.bot.guild-id");
      if (guildStr == null || guildStr.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(Long.parseLong(guildStr.trim()));
    } catch (Exception ex) {
      logger.warn("discord.bot.guild-id is set but invalid; falling back to global registration.");
      return Optional.empty();
    }
  }
}
