package com.velocitypowered.proxy.auth;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.velocitypowered.proxy.database.MongoDBManager;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;

public class AuthManager {
    private final MongoDBManager mongoDBManager;
    private final MongoCollection<Document> usersCollection;

    // Mapa trzyma tylko informację o tym, czy gracz jest zalogowany w obecnej sesji.
    private final Map<UUID, Boolean> authenticatedUsers = new ConcurrentHashMap<>();

    public AuthManager(MongoDBManager mongoDBManager) {
        this.mongoDBManager = mongoDBManager;
        // Pobieramy kolekcję "users" (powinna zostać utworzona wcześniej w mongoDBManager.setupDatabase())
        MongoDatabase database = mongoDBManager.getDatabase();
        this.usersCollection = database.getCollection("users");
    }

    /**
     * Rejestruje nowego użytkownika w bazie.
     * @param uuid     UUID gracza
     * @param password Hasło do ustawienia
     * @return false, jeśli użytkownik już istnieje; true, jeśli rejestracja się powiodła
     */
    public boolean register(UUID uuid, String password) {
        // Sprawdzamy, czy gracz już istnieje w bazie
        Document existing = usersCollection.find(eq("uuid", uuid.toString())).first();
        if (existing != null) {
            // Gracz jest już w bazie => nie można zarejestrować ponownie
            return false;
        }

        // Hashujemy hasło
        String hashedPassword = hashPassword(password);

        // Tworzymy dokument w Mongo
        Document doc = new Document("uuid", uuid.toString())
                .append("password", hashedPassword);

        // Wstawiamy do kolekcji
        usersCollection.insertOne(doc);
        return true;
    }

    /**
     * Loguje użytkownika, sprawdzając hasło z bazy.
     * @param playerId UUID gracza
     * @param password Podane hasło
     * @return true, jeśli logowanie się powiodło; false - błędne hasło lub brak w bazie
     */
    public boolean login(UUID playerId, String password) {
        // Szukamy użytkownika w kolekcji "users"
        Document userDoc = usersCollection.find(eq("uuid", playerId.toString())).first();
        if (userDoc == null) {
            // Brak w bazie => nie można się zalogować
            return false;
        }

        // Pobieramy zahashowane hasło
        String hashedPassword = userDoc.getString("password");
        if (hashedPassword == null || !checkPassword(password, hashedPassword)) {
            // Hasło nieprawidłowe
            return false;
        }

        // Zaznaczamy w pamięci, że gracz jest w tej sesji zalogowany
        authenticatedUsers.put(playerId, true);
        return true;
    }

    /**
     * Wylogowuje gracza z pamięci.
     */
    public void logout(UUID playerId) {
        authenticatedUsers.remove(playerId);
    }

    /**
     * Informacja, czy gracz jest zalogowany w tej sesji (w pamięci).
     */
    public boolean isAuthenticated(UUID playerId) {
        return authenticatedUsers.getOrDefault(playerId, false);
    }

    /**
     * Haszowanie hasła z użyciem BCrypt.
     */
    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Weryfikacja hasła przy logowaniu.
     */
    private boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
}
