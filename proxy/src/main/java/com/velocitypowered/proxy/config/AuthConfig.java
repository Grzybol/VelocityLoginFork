package com.velocitypowered.proxy.config;

import com.velocitypowered.api.proxy.ProxyServer;
import com.moandjiezana.toml.Toml;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AuthConfig {
    private final List<String> authServers;
    private final String authServer;

    public AuthConfig(ProxyServer server, Path dataDirectory) {
        // Ścieżka do pliku konfiguracyjnego Velocity
        Path configPath = dataDirectory.resolve("velocity.toml");

        // 1. Najpierw dopisujemy brakującą sekcję [auth] (jeśli potrzeba).
        ensureAuthSectionExists(configPath);

        // 2. Wczytujemy config ponownie do obiektu Toml
        Toml toml = new Toml().read(configPath.toFile());
        Toml authTable = toml.getTable("auth");

        // Zwróci null, jeśli ktoś całkowicie usunął "[auth]" z pliku w trakcie operacji
        // lub występuje jakiś inny nietypowy problem.
        if (authTable == null) {
            throw new IllegalStateException("Brak sekcji [auth] w configu velocity.toml, mimo prób utworzenia!");
        }

        // 3. Czytamy listę serwerów i nazwę serwera auth
        // Można tu jeszcze dodać obronę np. przed tym, że authServers może być niewłaściwym typem itd.
        this.authServers = authTable.getList("authServers");
        this.authServer = authTable.getString("authServer");

        // Ewentualna walidacja:
        if (this.authServers == null || this.authServers.isEmpty()) {
            throw new IllegalStateException("authServers jest puste lub niepoprawne w [auth] sekcji velocity.toml");
        }
        if (this.authServer == null || this.authServer.isEmpty()) {
            throw new IllegalStateException("authServer jest pusty w [auth] sekcji velocity.toml");
        }
    }

    /**
     * Sprawdza czy w pliku jest sekcja [auth], a jeśli nie - dopisuje ją na końcu wraz
     * z wartościami domyślnymi.
     */
    private void ensureAuthSectionExists(Path configPath) {
        if (!Files.exists(configPath)) {
            // Plik w ogóle nie istnieje - tutaj możesz zdecydować, czy chcesz tworzyć cały
            // velocity.toml z jakimś minimalnym szablonem, czy rzucić wyjątek.
            return;
        }

        try {
            // Wczytujemy wszystkie linie
            List<String> lines = Files.readAllLines(configPath);

            boolean hasAuthSection = lines.stream().anyMatch(line -> line.trim().equals("[auth]"));

            if (!hasAuthSection) {
                // Dopisujemy brakującą sekcję [auth] na końcu
                try (BufferedWriter writer = Files.newBufferedWriter(configPath, java.nio.file.StandardOpenOption.APPEND)) {
                    writer.newLine();
                    writer.write("[auth]");
                    writer.newLine();
                    writer.write("# Domyślna lista serwerów, na które przerzucimy gracza po zalogowaniu:");
                    writer.newLine();
                    writer.write("authServers = [\"test\", \"factions\", \"minigames\"]");
                    writer.newLine();
                    writer.write("# Nazwa serwera, do którego trafia nowo połączony gracz w celu zalogowania:");
                    writer.newLine();
                    writer.write("authServer = \"auth\"");
                    writer.newLine();
                    // Możesz dodać kolejne linie w razie potrzeby.
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Nie udało się dopisać sekcji [auth] do pliku configu!", e);
        }
    }

    public List<String> getAuthServers() {
        return authServers;
    }

    public String getAuthServer() {
        return authServer;
    }
}
