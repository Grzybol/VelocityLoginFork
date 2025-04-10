package com.velocitypowered.proxy.auth;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.database.MongoDBManager;
import com.velocitypowered.proxy.lang.LangConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bson.Document;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class AuthManager {

    private final MongoDBManager mongoDBManager;
    private final ProxyServer server;
    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> antyVPNCollection;
    private final AuthConfig authConfig;

    // Mapa trzyma informację, czy gracz jest zalogowany (w danej instancji proxy)
    private final Map<UUID, Boolean> authenticatedUsers = new ConcurrentHashMap<>();
    // Kiedy gracz ostatnio się poprawnie zalogował? (epoch second)
    //private final Map<UUID, Long> lastLoginTime = new ConcurrentHashMap<>();
    private final Map<UUID, LastLoginInfo> lastLoginTime = new ConcurrentHashMap<>();

    // Liczba nieudanych prób logowania
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    // Do kiedy gracz jest zablokowany (epoch second)
    private final Map<UUID, Long> blockedUntil = new ConcurrentHashMap<>();
    private record LastLoginInfo(long timestamp, String ip) {}
    private final LangConfig langConfig;


    public AuthManager(MongoDBManager mongoDBManager, AuthConfig authConfig,ProxyServer server,LangConfig langConfig) {
        this.mongoDBManager = mongoDBManager;
        this.authConfig = authConfig;
        this.langConfig = langConfig;
        this.server = server;
        MongoDatabase database = mongoDBManager.getDatabase();
        this.usersCollection = database.getCollection("users");
        this.antyVPNCollection = database.getCollection("antyVPN");
    }

    // ======================== Rejestracja i sprawdzanie istnienia ========================
    public boolean isRegistered(UUID uuid) {
        Document existing = usersCollection.find(eq("uuid", uuid.toString())).first();
        return (existing != null);
    }
    public boolean isAddressSaved(String ip) {
        Document query = new Document("ip", ip);
        Document result = antyVPNCollection.find(query).first();
        logger.info("isAddressSaved result: {}", result);
        return result != null;
    }
    public boolean isVPNInDatabase(String ip) {
        Document query = new Document("ip", ip);
        Document result = antyVPNCollection.find(query).first();

        // Jeśli IP nie istnieje w bazie, zwracamy false
        if (result == null) {
            return false;
        }
        logger.info("VPN check result: {}", result.getString("proxy"));

        // Sprawdzamy, czy pole "proxy" ma wartość "yes"
        return "yes".equalsIgnoreCase(result.getString("proxy"));
    }
    public boolean isFromCountry(String ip, List<String> country) {
        Document query = new Document("ip", ip);
        Document result = antyVPNCollection.find(query).first();

        // Jeśli IP nie istnieje w bazie, zwracamy false
        if (result == null) {
            return false;
        }
        logger.info("VPN check result: {}", result.getString("country"));
        return country.contains(result.getString("country"));
    }
    public void saveToAntyVPN(InetAddress ip) {
        Document doc = proxyCheck(ip);
        if (doc == null) {
            logger.warn("No data received from ProxyCheck for IP: {}", ip.getHostAddress());
            return;
        }

        if (isAddressSaved(ip.getHostAddress())) {
            antyVPNCollection.replaceOne(
                    Filters.eq("ip", ip.getHostAddress()), // Find the existing record by IP
                    doc, // New document to replace the old one
                    new ReplaceOptions().upsert(true) // Upsert = Insert if not found, otherwise update
            );
            logger.info("Updated VPN check data for IP: {}", ip.getHostAddress());
            return;
        }

        antyVPNCollection.insertOne(doc);
        logger.info("Saved VPN check data for IP: {}", ip.getHostAddress());
    }
    public Document proxyCheck(InetAddress ip) {
        String apiKey = authConfig.getApiKey();
        String url = "https://proxycheck.io/v2/" + ip.getHostAddress() + "?key=" + apiKey + "&vpn=1&asn=1&risk=1";
        logger.info("VPN check URL: {}", url);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String response = reader.lines().collect(Collectors.joining("\n"));

                logger.info("VPN check response: {}", response);

                JSONObject json = new JSONObject(response);
                if (!json.has(ip.getHostAddress())) {
                    logger.warn("No data received from ProxyCheck for IP: {}, response: {}", ip.getHostAddress(), response);
                    return null;  // Jeśli odpowiedź nie zawiera danych o IP
                }

                JSONObject data = json.getJSONObject(ip.getHostAddress());

                // Tworzymy dokument do zapisania w bazie MongoDB
                Document doc = new Document("ip", ip.getHostAddress())
                        .append("asn", data.optString("asn", ""))
                        .append("range", data.optString("range", ""))
                        .append("hostname", data.optString("hostname", ""))
                        .append("provider", data.optString("provider", ""))
                        .append("organisation", data.optString("organisation", ""))
                        .append("continent", data.optString("continent", ""))
                        .append("continentcode", data.optString("continentcode", ""))
                        .append("country", data.optString("country", ""))
                        .append("isocode", data.optString("isocode", ""))
                        .append("region", data.optString("region", ""))
                        .append("regioncode", data.optString("regioncode", ""))
                        .append("timezone", data.optString("timezone", ""))
                        .append("city", data.optString("city", ""))
                        .append("postcode", data.optString("postcode", ""))
                        .append("latitude", data.optDouble("latitude", 0.0))
                        .append("longitude", data.optDouble("longitude", 0.0))
                        .append("currency_code", data.optJSONObject("currency") != null ? data.getJSONObject("currency").optString("code", "") : "")
                        .append("currency_name", data.optJSONObject("currency") != null ? data.getJSONObject("currency").optString("name", "") : "")
                        .append("currency_symbol", data.optJSONObject("currency") != null ? data.getJSONObject("currency").optString("symbol", "") : "")
                        .append("devices_address", data.optJSONObject("devices") != null ? data.getJSONObject("devices").optInt("address", 0) : 0)
                        .append("devices_subnet", data.optJSONObject("devices") != null ? data.getJSONObject("devices").optInt("subnet", 0) : 0)
                        .append("proxy", data.optString("proxy", "no"))
                        .append("type", data.optString("type", "unknown"))
                        .append("check_timestamp", System.currentTimeMillis());

                return doc;
            }
        } catch (Exception e) {
            logger.error("Failed to check VPN status", e);
            return null;
        }
    }
    public boolean register(UUID uuid, String password,String ip, String playerName) {
        if (isRegistered(uuid)) {
            return false;
        }
        String hashedPassword = hashPassword(password);
        Document doc = new Document("uuid", uuid.toString())
                .append("password", hashedPassword)
                .append("registeredIP",ip)
                .append("registeredAt", Instant.now().getEpochSecond())
                .append("lastLogin", 0)
                .append("lastIP", "")
                .append("playerName",playerName);

        usersCollection.insertOne(doc);
        return true;
    }
    public long getBlockedUntilTime(UUID playerId) {
        return blockedUntil.getOrDefault(playerId, 0L);
    }

    // ======================== Logowanie i wylogowanie ========================

    /**
     * Logowanie z hasłem (klasyczne).
     * Zwraca true/false w zależności od powodzenia (czy hasło poprawne w bazie).
     * Jeśli sukces, ustawia authenticatedUsers = true, lastLoginTime oraz resetuje blokady.
     */
    public boolean login(UUID playerId, String password, String ip,String playerName) {
        logger.info("Logging in player {}", playerId);
        Document userDoc = usersCollection.find(eq("uuid", playerId.toString())).first();
        if (userDoc == null) {
            return false;
        }

        String hashedPassword = userDoc.getString("password");
        if (hashedPassword == null || !checkPassword(password, hashedPassword)) {
            return false;
        }

        // Sukces logowania
        authenticatedUsers.put(playerId, true);
        ConnectedPlayer player = server.getPlayer(playerId).map(ConnectedPlayer.class::cast).orElse(null);
        long loginTimestamp =  System.currentTimeMillis();
        lastLoginTime.put(playerId, new LastLoginInfo(loginTimestamp, ip));
        logger.info("lastLoginTime player: "+lastLoginTime.get(playerId)+", player: "+playerId+", loginTimestamp: "+loginTimestamp+", ip: "+ip);
        failedAttempts.remove(playerId);
        blockedUntil.remove(playerId);
        // Update last login time and IP
        Document updateDoc = new Document("$set", new Document("lastLogin", loginTimestamp).append("lastIP", ip));

        // Sprawdzenie i aktualizacja nazwy gracza, jeśli nie istnieje
        if (userDoc.getString("playerName") == null || userDoc.getString("playerName").isEmpty()) {
            updateDoc.get("$set", new Document()).append("playerName", playerName);
        }

        usersCollection.updateOne(eq("uuid", playerId.toString()), updateDoc);
        return true;
    }


    public void logout(UUID playerId, String ip) {
        if (hasValidSession(playerId,ip)) {
            logger.info("Logging out player {}", playerId);
            authenticatedUsers.remove(playerId);
            long loginTimestamp =  System.currentTimeMillis();
            lastLoginTime.put(playerId, new LastLoginInfo(loginTimestamp, ip));
            logger.info("lastLoginTime player: "+lastLoginTime.get(playerId)+", player: "+playerId+", loginTimestamp: "+loginTimestamp+", ip: "+ip);
        }

        //failedAttempts.remove(playerId);
        //blockedUntil.remove(playerId);
    }

    /**
     * Sprawdza, czy gracz jest zalogowany w pamięci.
     */
    public boolean isAuthenticated(UUID playerId) {
        return authenticatedUsers.getOrDefault(playerId, false);
    }

    // ======================== Próby logowania / blokada ========================

    /**
     * Jeżeli próba logowania się nie udała - wywołaj tę metodę, aby zwiększyć licznik i ewentualnie zablokować gracza.
     * Zwraca true, jeśli właśnie ZABLOKOWANO gracza, false w przeciwnym wypadku.
     */
    public boolean incrementFailedAttempt(UUID playerId) {
        int attempts = failedAttempts.getOrDefault(playerId, 0);
        attempts++;
        failedAttempts.put(playerId, attempts);

        if (attempts >= authConfig.getMaxPasswordAttempts()) {
            long blockUntilTime = Instant.now().getEpochSecond() + authConfig.getAttemptFailedLoginDelay();
            blockedUntil.put(playerId, blockUntilTime);
            failedAttempts.remove(playerId);
            return true;
        }
        return false;
    }

    /**
     * Czy gracz jest aktualnie zablokowany z powodu zbyt wielu nieudanych prób?
     */
    public boolean isBlocked(UUID playerId) {
        long now = Instant.now().getEpochSecond();
        long blockedTime = blockedUntil.getOrDefault(playerId, 0L);
        return now < blockedTime;
    }

    // ======================== Sesja (auto-login) ========================

    /**
     * Czy gracz ma wciąż ważną sesję (od ostatniego logowania nie minęło sessionLength sekund)?
     */
    /*
    public boolean hasValidSession(UUID playerId) {
        Long lastTime = lastLoginTime.get(playerId);
        if (lastTime == null) {
            logger.info("No last login time for player {}", playerId);
            return false;
        }
        long now = Instant.now().getEpochSecond();
        long diff = now - lastTime;
        logger.info("Session length: {}", diff+", last login time: "+formatTime(lastTime)+" player: "+playerId);
        return diff < authConfig.getSessionLength();
    }

     */
    public boolean hasValidSession(UUID playerId,String ip) {
        LastLoginInfo info = lastLoginTime.get(playerId);
        if (info == null) {
            logger.info("No last login info for player {}", playerId);
            return false;
        }
        if (!info.ip().equals(ip)) {
            logger.info("IP mismatch for player {}", playerId);
            return false;
        }
        long sessionLength = authConfig.getSessionLength()*1000;//sekundy na milisekundy
        long lastTime = info.timestamp();
        long now = System.currentTimeMillis();
        long diff = now - lastTime;
        logger.info("Session length: {}, last login time: {}, sessionLength: {}", diff, lastTime,sessionLength);
        logger.info("player: "+playerId+", loginTimestamp: "+lastTime+", ip: "+ip);
        if(diff > sessionLength) {
            logger.info("Session expired for player {}", playerId);
            lastLoginTime.remove(playerId);
            return false;
        }else {
            logger.info("Session still valid for player {}", playerId);
            return true;
        }
    }

    public String formatTime(long time) {
        return Instant.ofEpochSecond(time).toString();
    }

    /**
     * Próba automatycznego logowania, jeśli sesja jest jeszcze aktywna.
     */
    public boolean tryAutoLoginIfSessionActive(UUID playerId,String ip) {
        logger.info("Trying auto-login for player {}", playerId);

        if (!hasValidSession(playerId,ip)) {
            logger.info("Session is not valid for player {}", playerId);
            return false;
        }
        // Sesja jest ważna => auto-logowanie
        authenticatedUsers.put(playerId, true);
        long loginTimestamp =  System.currentTimeMillis();
        lastLoginTime.put(playerId, new LastLoginInfo(loginTimestamp, ip));
        logger.info("Auto-login for player {}", playerId);
        return true;
    }

    // ======================== Pomocnicze ========================
    public boolean isValidPassword(String password) {
        if (password.length() < 6) {
            return false;
        }
        // Sprawdzamy, czy hasło ma co najmniej 1 znak specjalny
        return password.matches(".*[^a-zA-Z0-9].*");
    }
    // ========== PRZENOSZENIE NA INNY SERWER ============
    private void sendToFirstAvailableServer(ConnectedPlayer player) {
        Optional<RegisteredServer> targetServer = authConfig.getAuthServers().stream()
                .filter(serverName -> !serverName.equalsIgnoreCase(authConfig.getAuthServer()))
                .map(server::getServer)
                .flatMap(Optional::stream)
                .findFirst();

        targetServer.ifPresentOrElse(
                srv -> player.createConnectionRequest(srv).fireAndForget(),
                () -> player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("no-auth-server")
                        )
                )
        );
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }

    // ======================== Zmiana hasła (opcjonalnie) ========================
    public boolean updatePassword(UUID uuid, String newPassword) {
        UUID offlinePlayerUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Document userDoc = usersCollection.find(eq("uuid", uuid.toString())).first();
        if (userDoc == null) {
            return false;
        }
        String hashed = hashPassword(newPassword);
        usersCollection.updateOne(eq("uuid", uuid.toString()),
                new Document("$set", new Document("password", hashed)));
        return true;
    }
    public boolean updatePassword(String playerName, String newPassword) {
        Document userDoc = usersCollection.find(eq("playerName",playerName)).first();
        if (userDoc == null) {
            return false;
        }
        String hashed = hashPassword(newPassword);
        usersCollection.updateOne(eq("playerName", playerName),
                new Document("$set", new Document("password", hashed)));
        return true;
    }
    public boolean updatePassword(UUID uuid, String oldPassword, String newPassword) {
        Document userDoc = usersCollection.find(eq("uuid", uuid.toString())).first();
        if (userDoc == null) {
            return false;
        }
        String hashedPassword = userDoc.getString("password");
        if (hashedPassword == null || !checkPassword(oldPassword, hashedPassword)) {
            return false;
        }
        String hashed = hashPassword(newPassword);
        usersCollection.updateOne(eq("uuid", uuid.toString()),
                new Document("$set", new Document("password", hashed)));
        return true;
    }
    public int getNumberOfAccounts(String ip){
        Document query = new Document("ip", ip);
        Document result = usersCollection.find(query).first();
        if (result == null) {
            return 0;
        }
        int count = (int) usersCollection.countDocuments(query);
        logger.info("getNumberOfAccounts result: {}", count);
        return count;

    }
    public boolean isCheckValid(String ip) {
        Document query = new Document("ip", ip);
        Document result = antyVPNCollection.find(query).first();
        if (result == null) {
            logger.info("isCheckValid: IP {} not found in database.", ip);
            return false;
        }
        long checkTimestamp = 0;
        if (result.containsKey("check_timestamp")) {
            Object timestampObj = result.get("check_timestamp");

            // Upewnij się, że wartość jest liczbą
            if (timestampObj instanceof Number) {
                checkTimestamp = ((Number) timestampObj).longValue();
            } else {
                logger.warn("isCheckValid: Invalid check_timestamp format for IP {}", ip);
                return false;
            }
        } else {
            logger.info("isCheckValid: No check_timestamp for IP {}", ip);
            return false;
        }
        logger.info("isCheckValid checkTimestamp: {}", checkTimestamp);
        long currentTimestamp = System.currentTimeMillis();
        long timeout = authConfig.getAntyVpnCheckTimeoutHours()* 3600000L;
        long resultTime = currentTimestamp - checkTimestamp;
        logger.info("isCheckValid result: {}, resultTime: {}", resultTime > timeout,resultTime);
        if(resultTime > timeout) {
            return false;
        }
        logger.info("isCheckValid result: {}", result);
        return result != null;
    }
}
