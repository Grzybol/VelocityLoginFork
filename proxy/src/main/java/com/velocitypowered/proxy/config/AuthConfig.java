package com.velocitypowered.proxy.config;

import com.velocitypowered.api.proxy.ProxyServer;
import com.moandjiezana.toml.Toml;
import org.slf4j.ILoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class AuthConfig {
    private final List<String> authServers;
    private final String authServer;

    private final long sessionLength;           // w sekundach
    private final int maxLoginTimeout;          // ile sekund czekać na zalogowanie
    private final int maxPasswordAttempts;      // ile razy można błędnie wpisać hasło
    private final long attemptFailedLoginDelay; // ile sekund blokady
    private final String PREFIX;
    private final String apiKey;
    private final int antyVpnCheckTimeoutHours;
    private final int maxAccountsPerIp;
    private final List<String> allowedCountryList;

    public AuthConfig(ProxyServer server, Path dataDirectory) {
        Path configPath = dataDirectory.resolve("velocity.toml");

        // 1. Dopisujemy brakujące elementy w pliku.
        ensureAuthKeysExist(configPath);

        // 2. Wczytujemy ponownie
        Toml toml = new Toml().read(configPath.toFile());
        Toml authTable = toml.getTable("auth");
        if (authTable == null) {
            throw new IllegalStateException("Brak sekcji [auth] w velocity.toml, mimo próby utworzenia!");
        }

        // 3. Odczyt parametrów (z domyślnymi wartościami, jeśli w pliku brak jakiegoś klucza)
        this.authServers = authTable.getList("authServers");
        this.authServer = authTable.getString("authServer");

        this.sessionLength = authTable.getLong("sessionLength", 600L);// default 600s
        this.maxPasswordAttempts = Math.toIntExact(authTable.getLong("maxPasswordAttempts", 3L));
        this.attemptFailedLoginDelay = authTable.getLong("attemptFailedLoginDelay", 30L);
        this.maxLoginTimeout = Math.toIntExact(authTable.getLong("maxLoginTimeout", 60L));
        this.apiKey = authTable.getString("apiKey", "1234567890");
        this.antyVpnCheckTimeoutHours = Math.toIntExact(authTable.getLong("antyVpnCheckTimeoutHours", 24L));
        this.maxAccountsPerIp = Math.toIntExact(authTable.getLong("maxAccountsPerIp", 3L));
        this.PREFIX = authTable.getString("prefix", "<gold><bold>[BetterServer]</bold></gold> ");
        // Wczytanie listy krajów, domyślnie jeśli brak, to np. ["PL", "US"]
        List<String> defaultCountries = List.of("Poland", "Germany", "United States");
        this.allowedCountryList = authTable.contains("allowedCountry")
                ? authTable.getList("allowedCountry")
                : defaultCountries;


        // 4. Walidacja
        if (this.authServers == null || this.authServers.isEmpty()) {
            throw new IllegalStateException("authServers jest puste w [auth] sekcji velocity.toml");
        }
        if (this.authServer == null || this.authServer.isEmpty()) {
            throw new IllegalStateException("authServer jest pusty w [auth] sekcji velocity.toml");
        }
    }

    /**
     * Metoda, która sprawdza:
     * 1) Czy w ogóle jest [auth], jeśli nie - dopisuje cały blok
     * 2) Jeśli jest [auth], to sprawdza, czy każdy klucz występuje (np. "authServers ="),
     *    jeśli nie, dopisuje go z wartością domyślną na końcu pliku.
     */
    private void ensureAuthKeysExist(Path configPath) {
        if (!Files.exists(configPath)) {
            // Można ewentualnie stworzyć minimalny "velocity.toml", ale w praktyce przeważnie już istnieje.
            return;
        }

        try {
            List<String> lines = Files.readAllLines(configPath);

            boolean hasAuthSection = lines.stream()
                    .anyMatch(line -> line.trim().equals("[auth]"));

            if (!hasAuthSection) {
                // Brak sekcji [auth] => dopisujemy cały blok
                try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardOpenOption.APPEND)) {
                    writer.newLine();
                    writer.write("[auth]");
                    writer.newLine();
                    writer.write("# Domyślna lista serwerów, na które przerzucimy gracza po zalogowaniu:");
                    writer.newLine();
                    writer.write("authServers = [\"test\", \"factions\", \"minigames\"]");
                    writer.newLine();
                    writer.write("# Nazwa serwera, do którego trafia nowy gracz w celu zalogowania:");
                    writer.newLine();
                    writer.write("authServer = \"auth\"");
                    writer.newLine();
                    writer.write("# Ile sekund trwa sesja po zalogowaniu? (domyślnie 600 = 10 min)");
                    writer.newLine();
                    writer.write("sessionLength = 600");
                    writer.newLine();
                    writer.write("# Ile razy można błędnie wpisać hasło?");
                    writer.newLine();
                    writer.write("maxPasswordAttempts = 3");
                    writer.newLine();
                    writer.write("# Po ilu sekundach od przekroczenia liczby prób znów można logować?");
                    writer.newLine();
                    writer.write("attemptFailedLoginDelay = 30");
                    writer.newLine();
                    writer.write("# Ile sekund czekać na zalogowanie?");
                    writer.newLine();
                    writer.write("maxLoginTimeout = 60");
                    writer.newLine();
                    writer.write("prefix = \"<gold><bold>[BetterServer]</bold></gold> \"");
                    writer.newLine();
                    writer.write("apiKey = \"1234567890\"");
                    writer.newLine();
                    writer.write("antyVpnCheckTimeoutHours = 24");
                    writer.newLine();
                    writer.write("maxAccountsPerIp = 3");
                    writer.newLine();
                    writer.write("allowedCountry = \"Poland\"");
                }
            } else {
                // Sekcja [auth] istnieje -> sprawdzamy poszczególne klucze
                lines = appendKeyIfMissing(lines, "authServers =",
                        "# Domyślna lista serwerów, na które przerzucimy gracza po zalogowaniu:",
                        "authServers = [\"test\", \"factions\", \"minigames\"]");
                lines = appendKeyIfMissing(lines, "authServer =",
                        "# Nazwa serwera, do którego trafia nowy gracz w celu zalogowania:",
                        "authServer = \"auth\"");
                lines = appendKeyIfMissing(lines, "sessionLength =",
                        "# Ile sekund trwa sesja po zalogowaniu? (domyślnie 600 = 10 min)",
                        "sessionLength = 600");
                lines = appendKeyIfMissing(lines, "maxPasswordAttempts =",
                        "# Ile razy można błędnie wpisać hasło?",
                        "maxPasswordAttempts = 3");
                lines = appendKeyIfMissing(lines, "attemptFailedLoginDelay =",
                        "# Po ilu sekundach od przekroczenia liczby prób znów można logować?",
                        "attemptFailedLoginDelay = 30");
                lines = appendKeyIfMissing(lines, "prefix =",
                        "#prefix = \"<gold><bold>[BetterServer]</bold></gold> \"",
                        "prefix = \"<gold><bold>[BetterServer]</bold></gold> \"");
                lines = appendKeyIfMissing(lines, "maxLoginTimeout =",
                        "# Ile sekund czekać na zalogowanie?",
                        "maxLoginTimeout = 60");
                lines = appendKeyIfMissing(lines, "apiKey =",
                        "# API Key",
                        "apiKey = \"1234567890\"");
                lines = appendKeyIfMissing(lines, "antyVpnCheckTimeoutHours =",
                        "# Ile godzin trwa blokada antyVPN?",
                        "antyVpnCheckTimeoutHours = 24");
                lines = appendKeyIfMissing(lines, "maxAccountsPerIp =",
                        "# Ile kont może być zarejestrowanych na jedno IP?",
                        "maxAccountsPerIp = 3");
                lines = appendKeyIfMissing(lines, "allowedCountry =",
                        "# Kraj, z którego można się zalogować",
                        "allowedCountry = \"Poland\"");

                // Po ewentualnym dopisaniu kluczy - zapisujemy plik
                Files.write(configPath, lines);
            }

        } catch (IOException e) {
            throw new RuntimeException("Nie udało się poprawnie obsłużyć sekcji [auth] w pliku configu!", e);
        }
    }

    /**
     * appendKeyIfMissing:
     *  - Sprawdza, czy w podanych liniach pliku występuje klucz (np. "authServers =")
     *  - Jeśli go nie ma, dodaje na końcu komentarz (comment) i wartość (valueLine).
     *  - Zwraca zaktualizowaną listę linii (nie zapisuje od razu do pliku).
     */
    private List<String> appendKeyIfMissing(List<String> lines, String keyFragment, String comment, String valueLine) {
        boolean found = lines.stream().anyMatch(line -> line.trim().startsWith(keyFragment));
        if (!found) {
            lines.add(""); // pusta linia
            lines.add(comment);
            lines.add(valueLine);
        }
        return lines;
    }

    // GETTERY
    public List<String> getAuthServers() {
        return authServers;
    }

    public String getAuthServer() {
        return authServer;
    }

    public long getSessionLength() {
        return sessionLength;
    }
    public String getPrefix() {
        return PREFIX;
    }

    public int getMaxPasswordAttempts() {
        return maxPasswordAttempts;
    }

    public long getAttemptFailedLoginDelay() {
        return attemptFailedLoginDelay;
    }
    public int getMaxLoginTimeout() {
        return maxLoginTimeout;
    }
    public String getApiKey() {
        return apiKey;
    }
    public int getAntyVpnCheckTimeoutHours() {
        return antyVpnCheckTimeoutHours;
    }
    public int getMaxAccountsPerIp() {
        return maxAccountsPerIp;
    }
    public List<String> getAllowedCountryList() {
        return allowedCountryList;
    }
}
