package com.velocitypowered.proxy.session;

import com.velocitypowered.proxy.config.HybridAuthConfig;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class SessionValidator {

    private final HybridAuthConfig config;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SessionValidator(HybridAuthConfig config) {
        this.config = config;

    }

    public Optional<SessionValidationResult> validateSession(String username, String serverIdHash) {
        if (!config.isEnablePremiumVerification()) {
            return Optional.empty();
        }

        String cacheKey = username + ":" + serverIdHash;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < config.getSessionCacheDurationMs()) {
            logger.debug("Returning cached session for {}", cacheKey);
            return Optional.of(cached.result);
        } else {
            cache.remove(cacheKey);
        }

        try {
            String sessionServerUrl = config.getSessionServerUrl()
                    + "?username=" + username
                    + "&serverId=" + serverIdHash;

            HttpURLConnection conn = (HttpURLConnection) new URL(sessionServerUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    String json = sb.toString();
                    String id = parseField(json, "id");
                    String name = parseField(json, "name");
                    if (id != null && name != null) {
                        SessionValidationResult result = new SessionValidationResult(id, name);
                        cache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis()));
                        return Optional.of(result);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error contacting Mojang session server", e);
        }
        return Optional.empty();
    }

    private String parseField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static class CacheEntry {
        final SessionValidationResult result;
        final long timestamp;

        CacheEntry(SessionValidationResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}
