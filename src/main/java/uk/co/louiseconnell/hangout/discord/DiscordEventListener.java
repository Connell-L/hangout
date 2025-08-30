package uk.co.louiseconnell.hangout.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordEventListener implements ApplicationRunner {
    
    private final GatewayDiscordClient gateway;
    private final ReactionListener reactionListener;
    
    @Override
    public void run(ApplicationArguments args) {
        // Log on ready
        gateway.on(ReadyEvent.class, this::onReady).subscribe();
        
        // Handle reaction events
        gateway.on(ReactionAddEvent.class, reactionListener::handleReactionAdd).subscribe();
        gateway.on(ReactionRemoveEvent.class, reactionListener::handleReactionRemove).subscribe();
        
        log.info("Discord event listeners registered");
    }
    
    private Mono<Void> onReady(ReadyEvent event) {
        log.info("Bot is ready! Logged in as {}", event.getSelf().getUsername());
        return Mono.empty();
    }
}
