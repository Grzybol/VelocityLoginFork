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
import com.velocitypowered.proxy.lang.LangConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.TimeUnit;

public class AuthEventListener {

    private final AuthManager authManager;

    private ScheduledTask reminderTask;

    // Możesz zdefiniować sobie stałą ze wspólnym prefiksem:
    private final AuthConfig authConfig;
    private final LangConfig langConfig;

    public AuthEventListener(AuthManager authManager, AuthConfig authConfig, LangConfig langConfig) {
        this.authConfig = authConfig;
        this.langConfig = langConfig;
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
                            authConfig.getPrefix() + langConfig.getMessage("chat-blocked")
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
                            authConfig.getPrefix() + langConfig.getMessage("command-blocked")
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
                            authConfig.getPrefix() + langConfig.getMessage("not-logged-in")
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
