package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.lang.LangConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;

public class AuthCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final ProxyServer server;
    private final AuthConfig authConfig;
    private final LangConfig langConfig;

    public AuthCommand(AuthManager authManager, ProxyServer server, AuthConfig authConfig, LangConfig langConfig) {
        this.authManager = authManager;
        this.server = server;
        this.authConfig = authConfig;
        this.langConfig = langConfig;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof ConnectedPlayer player)) {
            invocation.source().sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + "<yellow>Only players can execute this command!</yellow>"
                    )
            );
            return;
        }
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Czy gracz ma wciąż ważną sesję => auto-login i przeniesienie
        if (authManager.hasValidSession(player.getUniqueId(),ip)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("session-still-valid")
                    )
            );
            sendToFirstAvailableServer(player);
            return;
        }

        // Sprawdź czy jest zablokowany
        if (authManager.isBlocked(player.getUniqueId())) {
            long now = System.currentTimeMillis() / 1000; // sekundy
            // Do kiedy zablokowany:
            // => authManager ma mapę blockedUntil, ale możemy też dać getter getBlockedUntilTime()
            //    lub liczyć to localnie. Dla przykładu:
            //    W AuthManager nie mamy gettera, więc  w sumie potrzebujesz np. public getBlockedUntilMap()
            //    lub innej metody. Dla minimalnego przykładu robimy copy-paste logic:
            //
            // (Lepszy design -> dodać metodę getBlockedUntil(playerId) w AuthManager).

            // Zakładamy, że AuthManager ma public Map<UUID, Long> getBlockedUntilMap() {return blockedUntil;}
            // jeżeli wolisz, lub stwórz metodę getBlockedTimeLeft(...) w AuthManager.

            long unblockedAt = authManager.getBlockedUntilTime(player.getUniqueId()); // Dodaj taką metodę w managerze
            long secondsLeft = unblockedAt - (now / 1);
            if (secondsLeft < 0) secondsLeft = 0;

            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("blocked-login-time-left")
                                    + secondsLeft + "</bold>s.</red>"
                    )
            );
            return;
        }

        // Parsowanie komend
        String[] args = invocation.arguments();
        String command = invocation.alias().toLowerCase();
        if (args.length < 1) {
            if (authManager.hasValidSession(player.getUniqueId(),ip)) {
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("already-logged-in")
                        )
                );
                sendToFirstAvailableServer(player);
                return;
            }
            if (command.equals("register")) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("register-usage")
                ));
                return;
            }
            if (command.equals("login")) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("login-usage")
                ));
                return;
            }
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    authConfig.getPrefix() + langConfig.getMessage("not-logged-in")
            ));
            return;
        }



        if (command.equals("register")) {
            if (args.length != 2) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("register-usage")
                ));
                return;
            }
            handleRegister(player, args[0], args[1]);
        } else if (command.equals("login")) {
            if (args.length != 1 && args.length != 2) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("login-usage")
                ));
                return;
            }
            handleLogin(player, args[0],ip);
        } else {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + "<red>Unknown authentication command.</red>"
                    )
            );
        }
    }

    // ========== REJESTRACJA ============
    private void handleRegister(ConnectedPlayer player, String pass1, String pass2) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        if (!pass1.equals(pass2)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("passwords-dont-match")
                    )
            );
            return;
        }
        if (!authManager.isValidPassword(pass1)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("password-dont-meet-exp")
                    )
            );
            return;
        }

        // Próba rejestracji
        if (authManager.register(player.getUniqueId(), pass1,ip)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("register-success")
                    )
            );

            player.disconnect(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("register-success"+" - re-join the server")
                    )
            );
            /*
            authManager.login(player.getUniqueId(), pass1,ip);
            player.setAuthenticated(true);
            sendToFirstAvailableServer(player);

             */
        } else {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("already-registered")
                    )
            );
        }
    }

    // ========== LOGOWANIE ============
    private void handleLogin(ConnectedPlayer player, String password,String ip) {
        // Może już jest zalogowany i ma ważną sesję
        if (player.isAuthenticated() && authManager.hasValidSession(player.getUniqueId(),ip)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("already-logged-in")
                    )
            );
            sendToFirstAvailableServer(player);
            return;
        }

        if (authManager.login(player.getUniqueId(), password,ip)) {
            player.setAuthenticated(true);
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("login-success")
                    )
            );
            sendToFirstAvailableServer(player);
        } else {
            // Nieudana próba logowania => zwiększamy licznik
            boolean justBlocked = authManager.incrementFailedAttempt(player.getUniqueId());
            if (justBlocked) {
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("blocked-login-time")
                                        + authConfig.getAttemptFailedLoginDelay() + "</bold> s.</red>"
                        )
                );
            } else {
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("invalid-password")
                        )
                );
            }
        }
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
                                authConfig.getPrefix() + langConfig.getMessage("no-available-servers")
                        )
                )
        );
    }
}
