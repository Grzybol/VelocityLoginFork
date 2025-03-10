package com.velocitypowered.proxy.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.session.SessionValidationResult;
import com.velocitypowered.proxy.session.SessionValidator;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class PremiumConnectionListener {

    private final SessionValidator validator;
    private final Map<String, String> serverIdMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Inject
    public PremiumConnectionListener(SessionValidator validator) {
        this.validator = validator;

    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String serverId = new BigInteger(130, random).toString(16);
        serverIdMap.put(username, serverId);
        logger.debug("Generated serverId for {}: {}", username, serverId);
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        String username = event.getUsername();
        String serverId = serverIdMap.remove(username);
        if (serverId == null || serverId.isEmpty()) {
            logger.debug("No serverId found for {}, treating as offline.", username);
            return;
        }

        CompletableFuture.supplyAsync(() -> validator.validateSession(username, serverId))
                .thenAccept(optionalResult -> {
                    if (optionalResult.isPresent()) {
                        SessionValidationResult r = optionalResult.get();
                        UUID onlineUuid = toUuid(r.getId());
                        GameProfile newProfile = event.getGameProfile()
                                .withId(onlineUuid)
                                .withName(r.getName());
                        event.setGameProfile(newProfile);
                        logger.debug("{} verified as PREMIUM with UUID {}", username, onlineUuid);
                    } else {
                        logger.debug("Offline mode for {}", username);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error during asynchronous validation for {}", username, ex);
                    return null;
                });
    }

    private UUID toUuid(String withoutDashes) {
        return UUID.fromString(
                withoutDashes.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                )
        );
    }
}
