package com.velocitypowered.proxy.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.session.SessionValidationResult;
import com.velocitypowered.proxy.session.SessionValidator;
import org.slf4j.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class PremiumConnectionListener {

    private final SessionValidator validator;
    private final Map<String, String> serverIdMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private PublicKey mojangPublicKey; // Klucz publiczny Mojang

    @Inject
    public PremiumConnectionListener(SessionValidator validator) {
        this.validator = validator;
        this.mojangPublicKey = fetchMojangPublicKey(); // Pobranie klucza Mojang przy starcie serwera
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        try {
            String username = event.getUsername();

            if (mojangPublicKey == null) {
                logger.error("Mojang public key not available! Skipping serverId generation.");
                return;
            }

            // Generowanie klucza AES (SecretKey)
            SecretKey secretKey = generateAESKey();

            // Generowanie Server ID
            String serverId = generateServerId("", mojangPublicKey, secretKey);
            serverIdMap.put(username, serverId);

            logger.info("Generated serverId for {}: {}", username, serverId);
        } catch (Exception e) {
            logger.error("Error generating serverId for {}", event.getUsername(), e);
        }
    }

    /**
     * Generuje Server ID zgodnie z dokumentacją Mojang
     */
    public static String generateServerId(String baseServerId, PublicKey publicKey, SecretKey secretKey) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.update(baseServerId.getBytes(StandardCharsets.ISO_8859_1));
            messageDigest.update(secretKey.getEncoded());
            messageDigest.update(publicKey.getEncoded());
            byte[] digestData = messageDigest.digest();

            return new BigInteger(1, digestData).toString(16);
        } catch (Exception e) {
            logger.info("Error generating serverId. Exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Pobiera klucz publiczny Mojang z ich API
     */
    private static PublicKey fetchMojangPublicKey() {
        try {
            URL url = new URL("https://api.minecraftservices.com/publickeys");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                logger.error("Failed to fetch Mojang public key. Response code: {}", conn.getResponseCode());
                return null;
            }
            logger.info("Fetched Mojang public key successfully.");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Parsowanie odpowiedzi JSON
                String publicKeyBase64 = extractPublicKey(response.toString());
                if (publicKeyBase64 == null) {
                    logger.error("Failed to extract Mojang public key from response.");
                    return null;
                }

                // Konwersja Base64 na klucz publiczny
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
                return keyFactory.generatePublic(keySpec);
            }
        } catch (Exception e) {
            logger.error("Error fetching Mojang public key: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parsuje odpowiedź JSON i wyciąga pierwszy dostępny public key Mojang
     */
    private static String extractPublicKey(String jsonResponse) {
        try {
            logger.info("Parsing Mojang public key JSON...");

            JSONObject json = new JSONObject(jsonResponse);

            if (!json.has("profilePropertyKeys")) {
                logger.error("JSON response does not contain 'profilePropertyKeys' field!");
                return null;
            }

            JSONArray keysArray = json.getJSONArray("profilePropertyKeys");
            if (keysArray.isEmpty()) {
                logger.error("No public keys found in 'profilePropertyKeys'!");
                return null;
            }

            JSONObject firstKeyObject = keysArray.getJSONObject(0); // Pobieramy pierwszy dostępny klucz
            if (!firstKeyObject.has("publicKey")) {
                logger.error("First entry in 'profilePropertyKeys' does not contain 'publicKey'!");
                return null;
            }

            String publicKeyBase64 = firstKeyObject.getString("publicKey");
            logger.info("Extracted Mojang public key (Base64): {}", publicKeyBase64);
            return publicKeyBase64;

        } catch (Exception e) {
            logger.error("Error parsing Mojang public key from response: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generuje klucz AES (128-bitowy)
     */
    private static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            return keyGen.generateKey();
        } catch (Exception e) {
            logger.info("Error generating AES key. Exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        logger.info("GameProfileRequestEvent - premium connection listener");

        String username = event.getUsername();
        String serverId = serverIdMap.remove(username);
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        if (serverId == null || serverId.isEmpty()) {
            logger.info("No serverId found for {}, treating as offline.", username);
            return;
        }

        CompletableFuture.supplyAsync(() -> validator.validateSession(username, serverId, ip))
                .thenAccept(optionalResult -> {
                    if (optionalResult.isPresent()) {
                        SessionValidationResult r = optionalResult.get();
                        UUID onlineUuid = toUuid(r.getId());
                        GameProfile newProfile = event.getGameProfile()
                                .withId(onlineUuid)
                                .withName(r.getName());
                        event.setGameProfile(newProfile);
                        logger.info("{} verified as PREMIUM with UUID {}", username, onlineUuid);
                    } else {
                        logger.info("Offline mode for {}", username);
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
