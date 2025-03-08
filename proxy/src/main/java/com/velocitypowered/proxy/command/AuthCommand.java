package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class AuthCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final ProxyServer server;
    private final AuthConfig authConfig;

    // Mapa: gracz -> czas ostatniego pomyślnego logowania (epoch seconds)
    private final Map<UUID, Long> lastLoginTime = new HashMap<>();

    // Mapa: gracz -> liczba nieudanych prób logowania
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();

    // Mapa: gracz -> do kiedy jest zablokowany
    private final Map<UUID, Long> blockedUntil = new HashMap<>();

    // Definicja "znaku specjalnego": cokolwiek nie będące literą/cyfrą.
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[^a-zA-Z0-9]");

    // Prefix, który chcemy dodać do każdej wiadomości
    private static final String PREFIX = "<gold><bold>[BetterServer]</bold></gold> ";

    public AuthCommand(AuthManager authManager, ProxyServer server, AuthConfig authConfig) {
        this.authManager = authManager;
        this.server = server;
        this.authConfig = authConfig;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof ConnectedPlayer player)) {
            invocation.source().sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>Only players can execute this command!</yellow>"
                    )
            );
            return;
        }

        // Sprawdź czy gracz ma aktywną sesję
        if (hasValidSession(player)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<green>You still have a valid session. No need to re-login.</green>"
                    )
            );
            return;
        }

        // Sprawdź czy gracz jest zablokowany
        if (isBlocked(player)) {
            long now = Instant.now().getEpochSecond();
            long unblockedAt = blockedUntil.getOrDefault(player.getUniqueId(), 0L);
            long secondsLeft = unblockedAt - now;
            if (secondsLeft < 0) secondsLeft = 0;
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>You are blocked from login attempts for <bold>" + secondsLeft + "</bold> more seconds.</red>"
                    )
            );
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 2 && args[0].equalsIgnoreCase("register")) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>Usage: <white>/register <password> <confirm></white> or <white>/login <password></white></yellow>"
                    )
            );
            return;
        }

        String command = invocation.alias().toLowerCase();

        // /register wymaga 2 argumentów, /login – 1 argumentu
        if (command.equals("register") && args.length != 2) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>Usage: <white>/register <password> <confirm></white></yellow>"
                    )
            );
            return;
        }
        if (command.equals("login") && args.length != 1 && args.length != 2) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>Usage: <white>/login <password></white></yellow>"
                    )
            );
            return;
        }

        switch (command) {
            case "register" -> handleRegister(player, args[0], args[1]);
            case "login"    -> handleLogin(player, args[0]);
            default -> player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>Unknown authentication command.</red>"
                    )
            );
        }
    }

    // ========== REJESTRACJA ============
    private void handleRegister(ConnectedPlayer player, String pass1, String pass2) {
        if (!pass1.equals(pass2)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>Passwords do not match!</red>"
                    )
            );
            return;
        }
        if (!isValidPassword(pass1)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>Password must be at least 6 chars and contain 1 special character!</red>"
                    )
            );
            return;
        }

        // Próba rejestracji
        if (authManager.register(player.getUniqueId(), pass1)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<green>Successfully registered! Now logging you in...</green>"
                    )
            );

            // logujemy
            authManager.login(player.getUniqueId(), pass1);
            player.setAuthenticated(true);
            lastLoginTime.put(player.getUniqueId(), Instant.now().getEpochSecond());
            failedAttempts.remove(player.getUniqueId());
            blockedUntil.remove(player.getUniqueId());

            sendToFirstAvailableServer(player);
        } else {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>You are already registered. Please use <white>/login</white>.</yellow>"
                    )
            );
        }
    }

    // ========== LOGOWANIE ============
    private void handleLogin(ConnectedPlayer player, String password) {
        if (player.isAuthenticated() && hasValidSession(player)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<yellow>You are already logged in!</yellow>"
                    )
            );
            return;
        }

        if (authManager.login(player.getUniqueId(), password)) {
            // Sukces logowania
            player.setAuthenticated(true);
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<green>Successfully logged in!</green>"
                    )
            );
            lastLoginTime.put(player.getUniqueId(), Instant.now().getEpochSecond());

            failedAttempts.remove(player.getUniqueId());
            blockedUntil.remove(player.getUniqueId());

            sendToFirstAvailableServer(player);
        } else {
            // Nieudana próba
            incrementFailedAttempt(player);
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>Incorrect password or you're not registered.</red>"
                    )
            );
        }
    }

    // ========== SPRAWDZANIE/INCREMENTOWANIE NIEUDANYCH PRÓB ============
    private void incrementFailedAttempt(ConnectedPlayer player) {
        UUID pid = player.getUniqueId();
        int attempts = failedAttempts.getOrDefault(pid, 0);
        attempts++;
        failedAttempts.put(pid, attempts);

        if (attempts >= authConfig.getMaxPasswordAttempts()) {
            long blockUntilTime = Instant.now().getEpochSecond() + authConfig.getAttemptFailedLoginDelay();
            blockedUntil.put(pid, blockUntilTime);
            failedAttempts.remove(pid);

            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            PREFIX + "<red>Too many login attempts! You are blocked for <bold>"
                                    + authConfig.getAttemptFailedLoginDelay() + "</bold> seconds.</red>"
                    )
            );
        }
    }

    private boolean isBlocked(ConnectedPlayer player) {
        UUID pid = player.getUniqueId();
        long now = Instant.now().getEpochSecond();
        long blocked = blockedUntil.getOrDefault(pid, 0L);
        return now < blocked;
    }

    // ========== SPRAWDZANIE SESJI ============
    private boolean hasValidSession(ConnectedPlayer player) {
        Long lastLogin = lastLoginTime.get(player.getUniqueId());
        if (lastLogin == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        long diff = now - lastLogin;
        return diff < authConfig.getSessionLength();
    }

    // ========== WALIDACJA HASŁA ============
    private boolean isValidPassword(String password) {
        if (password.length() < 6) {
            return false;
        }
        // Czy zawiera znak specjalny?
        return SPECIAL_CHAR.matcher(password).find();
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
                                PREFIX + "<red>No available server found.</red>"
                        )
                )
        );
    }
}
