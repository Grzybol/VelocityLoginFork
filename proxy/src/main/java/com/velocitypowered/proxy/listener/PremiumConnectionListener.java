package com.velocitypowered.proxy.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.lang.LangConfig;
import com.velocitypowered.proxy.session.SessionValidationResult;
import com.velocitypowered.proxy.session.SessionValidator;
import net.kyori.adventure.text.format.TextColor;
import org.slf4j.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.awt.*;
import java.math.BigInteger;
import java.net.InetAddress;
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
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class PremiumConnectionListener {

    private final SessionValidator validator;
    private final Map<String, String> serverIdMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Map<InetAddress, Boolean> isVPNmap = new ConcurrentHashMap<>();
    private PublicKey mojangPublicKey; // Klucz publiczny Mojang
    private final AuthConfig authConfig;
    private final AuthManager authManager;
    private final LangConfig langConfig;

    @Inject
    public PremiumConnectionListener(SessionValidator validator, AuthConfig authConfig, AuthManager authManager, LangConfig langConfig) {
        this.validator = validator;
        this.mojangPublicKey = fetchMojangPublicKey(); // Pobranie klucza Mojang przy starcie serwera
        this.authConfig = authConfig;
        this.authManager = authManager;
        this.langConfig = langConfig;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        logger.info("PreLoginEvent - premium connection listener");
        net.kyori.adventure.text.Component reasonVPN = net.kyori.adventure.text.Component.text(langConfig.getMessage("vpn-not-allowed")).color(TextColor.color(0xFF0000));
        net.kyori.adventure.text.Component reasonCountry = net.kyori.adventure.text.Component.text(langConfig.getMessage("country-not-allowed")+"Allowed countries: "+authConfig.getAllowedCountryList()).color(TextColor.color(0xFF0000));
        boolean isVPNfromMap = false;

        if(!authManager.isAddressSaved(event.getConnection().getRemoteAddress().getAddress().getHostAddress()) || !authManager.isCheckValid(event.getConnection().getRemoteAddress().getAddress().getHostAddress()) ) {
            logger.info("Saving IP to database: {}", event.getConnection().getRemoteAddress().getAddress());
            authManager.saveToAntyVPN(event.getConnection().getRemoteAddress().getAddress());
        }
        isVPNfromMap = authManager.isVPNInDatabase(event.getConnection().getRemoteAddress().getAddress().getHostAddress());
        logger.info("isVPNfromMap: {} for IP {}", isVPNfromMap, event.getConnection().getRemoteAddress().getAddress());
        if(isVPNfromMap) {
            logger.info("VPN detected for IP {}", event.getConnection().getRemoteAddress().getAddress());
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reasonVPN));
            return;
        }
        // Sprawdzenie kraju - DO ODBLOKOWANIA!!!!

        if(authConfig.getAllowedCountryList().contains("*")){ // Jeśli lista krajów jest pusta, to nie sprawdzamy kraju
            logger.info("Country check is disabled");
        } else if(!authManager.isFromCountry(event.getConnection().getRemoteAddress().getAddress().getHostAddress(), authConfig.getAllowedCountryList())) {
            logger.info("Country detected for IP {}", event.getConnection().getRemoteAddress().getAddress());
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reasonCountry));
            return;
        }



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
    /*
    public boolean isVPN(InetAddress ip) {
        String apiKey = authConfig.getApiKey();
        String url = "https://proxycheck.io/v2/" + ip.getHostAddress() + "?key=" + apiKey + "&vpn=1&asn=1&risk=1";
        logger.info("VPN check URL: {}", url);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            // Odczytaj całą odpowiedź
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String response = reader.lines().collect(Collectors.joining("\n"));

                logger.info("VPN check response: {}", response);
                return response.contains("\"proxy\": \"yes\"");
            }
        } catch (Exception e) {
            logger.error("Failed to check VPN status", e);
            return false;  // Fail-safe: allow connection if API fails
        }
    }

     */

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
