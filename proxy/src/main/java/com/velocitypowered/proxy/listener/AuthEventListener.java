package com.velocitypowered.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.command.AuthCommand;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.TimeUnit;

public class AuthEventListener {

    private final AuthManager authManager;

    private ScheduledTask reminderTask;

    // Możesz zdefiniować sobie stałą ze wspólnym prefiksem:
    private final AuthConfig authConfig;

    public AuthEventListener(AuthManager authManager, AuthConfig authConfig) {
        this.authConfig = authConfig;
        this.authManager = authManager;


        /*
        // Utwórz zadanie cykliczne
        this.reminderTask = proxyServer.getScheduler()
                .buildTask(pluginInstance, () -> {
                    proxyServer.getAllPlayers().stream()
                            .filter(player -> player instanceof ConnectedPlayer)
                            .map(player -> (ConnectedPlayer) player)
                            .filter(cp -> !cp.isAuthenticated()) // tylko gracze niezalogowani
                            .forEach(cp -> {
                                cp.sendMessage(
                                        MiniMessage.miniMessage().deserialize(
                                                PREFIX + "<yellow>Remember to <white>/login</white> or <white>/register</white>!"
                                        )
                                );
                            });
                })
                .repeat(15, TimeUnit.SECONDS)
                .schedule();

         */
    }

    // Blokowanie czatu
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        if (!player.isAuthenticated()) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + "<red><bold>You must log in before chatting!</bold></red>"
                    )
            );
        }
    }

    // Blokowanie komend
    @Subscribe
    public void onPlayerCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof ConnectedPlayer player)) {
            return;
        }

        String command = event.getCommand().toLowerCase();

        // Tylko /login i /register są dozwolone, reszta blokowana
        if (!player.isAuthenticated()
                && !(command.startsWith("login") || command.startsWith("register"))) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + "<red><bold>You must log in first! Use /login or /register.</bold></red>"
                    )
            );
        }
    }

    // Powiadomienie po wejściu na serwer
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        if (!player.isAuthenticated()) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + "<yellow>Please use <white>/register <password></white> or <white>/login <password></white> to continue."
                    )
            );
        }
    }

    // Wylogowanie po rozłączeniu
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        authManager.logout(player.getUniqueId());
    }
}
