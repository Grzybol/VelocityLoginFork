package com.velocitypowered.proxy.auth;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.database.MongoDBManager;
import com.velocitypowered.proxy.lang.LangConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static com.mongodb.client.model.Filters.eq;
import static com.velocitypowered.proxy.protocol.packet.chat.CommandHandler.logger;

public class AuthManager {

    private final MongoDBManager mongoDBManager;
    private final ProxyServer server;
    private final MongoCollection<Document> usersCollection;
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
    record LastLoginInfo(long timestamp, String ip) {}
    private final LangConfig langConfig;


    public AuthManager(MongoDBManager mongoDBManager, AuthConfig authConfig,ProxyServer server,LangConfig langConfig) {
        this.mongoDBManager = mongoDBManager;
        this.authConfig = authConfig;
        this.langConfig = langConfig;
        this.server = server;
        MongoDatabase database = mongoDBManager.getDatabase();
        this.usersCollection = database.getCollection("users");
    }

    // ======================== Rejestracja i sprawdzanie istnienia ========================
    public boolean isRegistered(UUID uuid) {
        Document existing = usersCollection.find(eq("uuid", uuid.toString())).first();
        return (existing != null);
    }

    public boolean register(UUID uuid, String password) {
        if (isRegistered(uuid)) {
            return false;
        }
        String hashedPassword = hashPassword(password);
        Document doc = new Document("uuid", uuid.toString())
                .append("password", hashedPassword);
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
    public boolean login(UUID playerId, String password, String ip) {
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
        long loginTimestamp =  System.currentTimeMillis();
        lastLoginTime.put(playerId, new LastLoginInfo(loginTimestamp, ip));
        logger.info("lastLoginTime player: "+lastLoginTime.get(playerId)+", player: "+playerId+", loginTimestamp: "+loginTimestamp+", ip: "+ip);
        failedAttempts.remove(playerId);
        blockedUntil.remove(playerId);
        return true;
    }

    public void logout(UUID playerId, String ip) {
        authenticatedUsers.remove(playerId);
        long loginTimestamp =  System.currentTimeMillis();
        lastLoginTime.put(playerId, new LastLoginInfo(loginTimestamp, ip));
        logger.info("lastLoginTime player: "+lastLoginTime.get(playerId)+", player: "+playerId+", loginTimestamp: "+loginTimestamp+", ip: "+ip);
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

        if (!hasValidSession(playerId,ip)) {
            logger.info("Session is not valid for player {}", playerId);
            return false;
        }
        // Sesja jest ważna => auto-logowanie
        authenticatedUsers.put(playerId, true);
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
        Document userDoc = usersCollection.find(eq("uuid", uuid.toString())).first();
        if (userDoc == null) {
            return false;
        }
        String hashed = hashPassword(newPassword);
        usersCollection.updateOne(eq("uuid", uuid.toString()),
                new Document("$set", new Document("password", hashed)));
        return true;
    }
}
