/*
 * Copyright (C) 2025 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.command;
import com.velocitypowered.api.command.SimpleCommand;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.Component;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;

public class AuthCommand implements SimpleCommand {

    private final AuthManager authManager;
    private final ProxyServer server; // potrzebne do przenoszenia graczy
    private final AuthConfig authConfig;

    public AuthCommand(AuthManager authManager, ProxyServer server, AuthConfig authConfig) {
        this.authManager = authManager;
        this.server = server;
        this.authConfig = authConfig;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof ConnectedPlayer player)) {
            invocation.source().sendMessage(Component.text("Only players can execute this command!"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /register <password> OR /login <password>"));
            return;
        }

        String command = invocation.alias().toLowerCase();
        String password = args[0];

        switch (command) {
            case "register" -> handleRegister(player, password);
            case "login" -> handleLogin(player, password);
            default -> player.sendMessage(Component.text("Unknown authentication command."));
        }
    }

    private void handleRegister(ConnectedPlayer player, String password) {
        if (authManager.register(player.getUniqueId(), password)) {
            player.sendMessage(Component.text("✔ Successfully registered! Now logging you in..."));
            authManager.login(player.getUniqueId(), password);
            player.setAuthenticated(true);
            sendToFirstAvailableServer(player); // <-- dynamiczne przenoszenie
        } else {
            player.sendMessage(Component.text("You are already registered. Please use /login."));
        }
    }

    private void handleLogin(ConnectedPlayer player, String password) {
        if (player.isAuthenticated()) {
            player.sendMessage(Component.text("You are already logged in!"));
            return;
        }

        if (authManager.login(player.getUniqueId(), password)) {
            player.setAuthenticated(true);
            player.sendMessage(Component.text("✅ Successfully logged in!"));
            sendToFirstAvailableServer(player);
        } else {
            player.sendMessage(Component.text("Incorrect password or you're not registered."));
        }
    }
    private void sendToFirstAvailableServer(ConnectedPlayer player) {
        Optional<RegisteredServer> targetServer = authConfig.getAuthServers().stream()
                .filter(serverName -> !serverName.equalsIgnoreCase(authConfig.getAuthServer())) // wykluczamy authServer!
                .map(server::getServer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        targetServer.ifPresentOrElse(
                srv -> player.createConnectionRequest(srv).fireAndForget(),
                () -> player.sendMessage(Component.text("⚠️ No available server found."))
        );
    }

    /*
    private void sendToFirstAvailableServer(ConnectedPlayer player) {
        // pobieramy wszystkie serwery, które NIE są lobby
        Optional<RegisteredServer> targetServer = server.getAllServers().stream()
                .filter(srv -> !srv.getServerInfo().getName().equalsIgnoreCase("auth"))
                .findFirst();

        targetServer.ifPresentOrElse(
                srv -> player.createConnectionRequest(srv).fireAndForget(),
                () -> player.sendMessage(Component.text("⚠️ No available server found."))
        );
    }

     */


    // Metoda pomocnicza do przenoszenia gracza
    private void sendToServer(ConnectedPlayer player, String serverName) {
        server.getServer(serverName).ifPresentOrElse(targetServer -> {
            player.createConnectionRequest(targetServer).fireAndForget();
        }, () -> {
            player.sendMessage(Component.text("⚠️ Destination server not found."));
        });
    }
}
