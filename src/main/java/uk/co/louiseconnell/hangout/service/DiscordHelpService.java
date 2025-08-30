package uk.co.louiseconnell.hangout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import uk.co.louiseconnell.hangout.service.commands.slash.SlashCommand;
import uk.co.louiseconnell.hangout.service.commands.text.TextCommand;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiscordHelpService {

  private final ObjectProvider<SlashCommand> slashCommandsProvider;
  private final ObjectProvider<TextCommand> textCommandsProvider;

  public DiscordHelpService(ObjectProvider<SlashCommand> slashCommandsProvider,
                            ObjectProvider<TextCommand> textCommandsProvider) {
    this.slashCommandsProvider = slashCommandsProvider;
    this.textCommandsProvider = textCommandsProvider;
  }

  public String buildHelpText() {
    List<String> lines = new ArrayList<>();
    lines.add("Here’s what I can do:");

    Map<String, String> slashDesc = loadDescriptions("commands/slash/*.json");
    List<String> slashLines = new ArrayList<>();
    for (SlashCommand cmd : slashCommandsProvider.stream().toList()) {
      String name = cmd.getName();
      String desc = slashDesc.getOrDefault(name, "");
      String label = "/" + name;
      if (desc == null || desc.isBlank()) {
        slashLines.add("- " + label);
      } else {
        slashLines.add("- " + label + ": " + desc);
      }
    }
    slashLines.sort(Comparator.naturalOrder());
    if (!slashLines.isEmpty()) {
      lines.add("");
      lines.add("Slash Commands:");
      lines.addAll(slashLines);
    }

    Map<String, String> textDesc = loadDescriptions("commands/text/*.json");
    List<String> textLines = new ArrayList<>();
    for (TextCommand cmd : textCommandsProvider.stream().toList()) {
      String name = cmd.getName();
      String desc = textDesc.getOrDefault(name, "");
      String label = name;
      if (desc == null || desc.isBlank()) {
        textLines.add("- " + label);
      } else {
        textLines.add("- " + label + ": " + desc);
      }
    }
    textLines.sort(Comparator.naturalOrder());
    if (!textLines.isEmpty()) {
      lines.add("");
      lines.add("Text Commands:");
      lines.addAll(textLines);
    }

    String out = String.join("\n", lines);
    if (out.length() > 1900) {
      out = out.substring(0, 1900) + "\n…";
    }
    return out;
  }

  private Map<String, String> loadDescriptions(String pattern) {
    Map<String, String> result = new HashMap<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    ObjectMapper mapper = new ObjectMapper();
    try {
      Resource[] resources = resolver.getResources(pattern);
      for (Resource r : resources) {
        try (InputStream is = r.getInputStream()) {
          JsonNode node = mapper.readTree(is);
          String name = node.path("name").asText("");
          String desc = node.path("description").asText("");
          if (!name.isEmpty()) {
            result.put(name, desc);
          }
        } catch (IOException ignored) {
        }
      }
    } catch (IOException ignored) {
    }
    return result;
  }
}
