package com.velocitypowered.proxy.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MongoDBManager {

    private static final Logger logger = LogManager.getLogger(MongoDBManager.class);

    private MongoClient mongoClient;
    private MongoDatabase database;

    // Uwaga: te wartości najlepiej pobrać dynamicznie z configu
    private final String connectionString;
    private final String databaseName;

    /**
     * Konstruktor, który może przyjmować parametry z pliku config (velocity.toml)
     */
    public MongoDBManager(String connectionString, String databaseName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
    }

    /**
     * Nawiązuje połączenie z MongoDB na podstawie connectionString i databaseName.
     * Możesz też obsłużyć autoryzację itp. w zależności od potrzeb.
     */
    public boolean connect() {
        try {
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            logger.info("Connected to MongoDB database: {}", databaseName);
            return true;
        } catch (Exception e) {
            logger.error("Error connecting to MongoDB", e);
            return false;
        }
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed.");
        }
    }

    /**
     * Przykład metody inicjującej bazę danych. Możesz ją wywołać zaraz po connect().
     */
    public void setupDatabase() {
        if (database == null) {
            logger.error("Database connection is not initialized!");
            return;
        }

        // Ensure users collection exists
        if (!database.listCollectionNames().into(new java.util.ArrayList<>()).contains("users")) {
            database.createCollection("users");
            logger.info("Created 'users' collection in MongoDB.");
        } else {
            logger.info("'users' collection already exists.");
        }
        // Ensure users collection exists
        if (!database.listCollectionNames().into(new java.util.ArrayList<>()).contains("antyVPN")) {
            database.createCollection("antyVPN");
            logger.info("Created 'antyVPN' collection in MongoDB.");
        } else {
            logger.info("'antyVPN' collection already exists.");
        }
    }
}
