package com.velocitypowered.proxy.config;

import com.moandjiezana.toml.Toml;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public class MongoConfig {

    private static final Logger logger = LogManager.getLogger(MongoConfig.class);

    private String connectionString;
    private String databaseName;

    public MongoConfig(Path dataDirectory) {
        Path configPath = dataDirectory.resolve("velocity.toml");

        // 1. Upewnij się, że sekcja [mongo] istnieje
        ensureMongoSectionExists(configPath);

        // 2. Wczytaj config TOML
        Toml toml = new Toml().read(configPath.toFile());
        Toml mongoTable = toml.getTable("mongo");

        if (mongoTable == null) {
            // Teoretycznie nie powinno się zdarzyć, bo dopisaliśmy sekcję
            throw new IllegalStateException("Brak sekcji [mongo], nawet po próbie dopisania jej do pliku!");
        }

        // 3. Pobierz parametry
        this.connectionString = mongoTable.getString("connectionString");
        this.databaseName = mongoTable.getString("databaseName");

        // Dodaj podstawową walidację
        if (this.connectionString == null || this.connectionString.isEmpty()) {
            throw new IllegalArgumentException("W pliku velocity.toml brakuje connectionString w sekcji [mongo].");
        }
        if (this.databaseName == null || this.databaseName.isEmpty()) {
            throw new IllegalArgumentException("W pliku velocity.toml brakuje databaseName w sekcji [mongo].");
        }
    }

    /**
     * Gdy brak sekcji [mongo] w configu velocity.toml, dopisuje ją z wartościami domyślnymi.
     */
    private void ensureMongoSectionExists(Path configPath) {
        if (!Files.exists(configPath)) {
            // Jeśli velocity.toml w ogóle nie istnieje, można go stworzyć, ale
            // w standardowym Velocity to raczej istnieje zawsze.
            logger.warn("Plik velocity.toml nie istnieje - nie można dopisać [mongo].");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(configPath);
            boolean hasMongoSection = lines.stream().anyMatch(line -> line.trim().equals("[mongo]"));

            if (!hasMongoSection) {
                logger.info("Sekcja [mongo] nie istnieje w velocity.toml - dopisuję z wartościami domyślnymi.");
                try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardOpenOption.APPEND)) {
                    writer.newLine();
                    writer.write("[mongo]");
                    writer.newLine();
                    writer.write("# Domyślne parametry połączenia do MongoDB:");
                    writer.newLine();
                    writer.write("connectionString = \"mongodb://localhost:27017\"");
                    writer.newLine();
                    writer.write("databaseName = \"authplugin\"");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            logger.error("Nie udało się odczytać/zapisać velocity.toml, aby dopisać [mongo].", e);
        }
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
