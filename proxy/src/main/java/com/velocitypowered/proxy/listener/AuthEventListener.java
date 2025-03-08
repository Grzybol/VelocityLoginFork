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

package com.velocitypowered.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.Component;

public class AuthEventListener {

    private final AuthManager authManager;

    public AuthEventListener(AuthManager authManager) {
        this.authManager = authManager;
    }

    // Blokowanie czatu
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        if (!player.isAuthenticated()) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(Component.text("❌ Please log in before chatting."));
        }
    }

    // Blokowanie komend
    @Subscribe
    public void onPlayerCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof ConnectedPlayer player)) {
            return;
        }

        String command = event.getCommand().toLowerCase();

        if (!player.isAuthenticated()
                && !(command.startsWith("login") || command.startsWith("register"))) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(Component.text("❌ You must log in first! Use /login or /register."));
        }
    }

    // Powiadomienie po wejściu na serwer
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        if (!player.isAuthenticated()) {
            player.sendMessage(Component.text("🔑 Please use /register <password> or /login <password> to continue."));
        }
    }

    // Wylogowanie po rozłączeniu
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        authManager.logout(player.getUniqueId());
    }
}
