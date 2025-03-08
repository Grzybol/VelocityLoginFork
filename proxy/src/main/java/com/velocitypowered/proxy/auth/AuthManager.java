package com.velocitypowered.proxy.auth;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.velocitypowered.proxy.database.MongoDBManager;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;

public class AuthManager {

    private final MongoDBManager mongoDBManager;
    private final MongoCollection<Document> usersCollection;

    /**
     * Czy gracz jest zalogowany w tej instancji proxy?
     */
    private final Map<UUID, Boolean> authenticatedUsers = new ConcurrentHashMap<>();

    /**
     * Kiedy gracz ostatnio się poprawnie zalogował?
     * Reprezentujemy to jako sekundowy timestamp (epoch second).
     */
    private final Map<UUID, Long> lastLoginTime = new ConcurrentHashMap<>();

    public AuthManager(MongoDBManager mongoDBManager) {
        this.mongoDBManager = mongoDBManager;
        // pobieramy kolekcję "users"
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

    // ======================== Logowanie i wylogowanie ========================

    /**
     * Logowanie z hasłem (klasyczne).
     * Ustawia authenticated = true oraz lastLoginTime, jeśli hasło jest poprawne.
     */
    public boolean login(UUID playerId, String password) {
        Document userDoc = usersCollection.find(eq("uuid", playerId.toString())).first();
        if (userDoc == null) {
            return false;
        }

        String hashedPassword = userDoc.getString("password");
        if (hashedPassword == null || !checkPassword(password, hashedPassword)) {
            return false;
        }

        authenticatedUsers.put(playerId, true);
        lastLoginTime.put(playerId, Instant.now().getEpochSecond());
        return true;
    }

    /**
     * Wylogowuje gracza z pamięci.
     * Usuwamy info, że jest zalogowany i czas logowania.
     */
    public void logout(UUID playerId) {
        authenticatedUsers.remove(playerId);
        lastLoginTime.remove(playerId);
    }

    /**
     * Sprawdza, czy gracz jest zalogowany w pamięci.
     */
    public boolean isAuthenticated(UUID playerId) {
        return authenticatedUsers.getOrDefault(playerId, false);
    }

    // ======================== Mechanizm automatycznego logowania ========================

    /**
     * Przykładowa metoda, którą można wywołać zaraz po przyjściu gracza na serwer (np. w PostLoginEvent).
     * Sprawdza, czy gracz był wcześniej zalogowany i czy jego sesja nie wygasła.
     *
     * @param playerId      UUID gracza
     * @param sessionLength maksymalny czas sesji w sekundach
     * @return true, jeśli gracz został automatycznie zalogowany; false, jeśli sesja wygasła lub nigdy się nie logował.
     */
    public boolean tryAutoLoginIfSessionActive(UUID playerId, long sessionLength) {
        // czy w ogóle był kiedyś zalogowany?
        if (!lastLoginTime.containsKey(playerId)) {
            return false;
        }
        // sprawdzamy, czy minęło mniej niż sessionLength sekund
        long lastTime = lastLoginTime.get(playerId);
        long now = Instant.now().getEpochSecond();
        long diff = now - lastTime;

        if (diff < sessionLength) {
            // Sesja nadal ważna => auto-logowanie
            authenticatedUsers.put(playerId, true);
            return true;
        } else {
            // Sesja wygasła
            return false;
        }
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

    // ======================== Prywatne metody pomocnicze ========================
    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
}
