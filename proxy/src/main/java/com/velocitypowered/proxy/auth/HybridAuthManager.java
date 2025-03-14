package com.velocitypowered.proxy.auth;

import com.velocitypowered.proxy.config.HybridAuthConfig;
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

public class HybridAuthManager {

    private final SessionValidator sessionValidator;
    private final HybridAuthConfig config;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> serverIdMap = new ConcurrentHashMap<>();

    public HybridAuthManager(SessionValidator sessionValidator, HybridAuthConfig config) {
        this.sessionValidator = sessionValidator;
        this.config = config;

    }

    public void prepareHandshakeData(String username) {
        String serverId = new BigInteger(130, random).toString(16);
        serverIdMap.put(username, serverId);
    }

    public Optional<String> getServerIdForUsername(String username) {
        return Optional.ofNullable(serverIdMap.remove(username));
    }

    public CompletableFuture<Optional<SessionValidationResult>> tryAuthenticatePremiumAsync(String username, String serverIdHash, String ip) {
        return CompletableFuture.supplyAsync(() -> sessionValidator.validateSession(username, serverIdHash, ip));
    }

    public UUID convertStringToUuid(String rawId) {
        return UUID.fromString(
                rawId.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                )
        );
    }
}
