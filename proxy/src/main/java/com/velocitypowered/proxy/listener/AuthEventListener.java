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
import com.velocitypowered.proxy.logging.elastic.PlayerLogContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AuthEventListener {

    private final AuthManager authManager;

    private ScheduledTask reminderTask;

    // Możesz zdefiniować sobie stałą ze wspólnym prefiksem:
    private final AuthConfig authConfig;
    private final LangConfig langConfig;
    private static final Logger logger = LogManager.getLogger(AuthEventListener.class);



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
            PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" tried to send a message but is not logged in");
        }
        PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" sent a message: "+event.getMessage());
    }

    // Blokowanie komend
    @Subscribe
    public void onPlayerCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof ConnectedPlayer player)) {
            return;
        }
        //PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" tried to execute command: "+event.getCommand());

        String fullCommand = event.getCommand().toLowerCase();
        String baseCommand = fullCommand.split(" ")[0]; // 👈 tylko pierwsze słowo

        List<String> allowedCommands = List.of("login", "l", "zaloguj", "register", "r", "rejestracja");
        //PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" tried to execute command: "+event.getCommand());

        if (!player.isAuthenticated() && !allowedCommands.contains(baseCommand)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("command-blocked")
                    )
            );
        }




        /*

        // Tylko /login i /register są dozwolone, reszta blokowana
        if (!player.isAuthenticated()
                && !(command.startsWith("login") || command.startsWith("register")||command.equals("l")||command.equals("zaloguj")||command.equals("r")||command.equals("rejestracja"))) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("command-blocked")
                    )
            );
        }

         */
    }

    // Powiadomienie po wejściu na serwer
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {

        ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
        PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" connected to server: "+event.getServer().getServerInfo().getName());
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

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        authManager.logout(player.getUniqueId(), ip);
        PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" logged out");
    }
}
