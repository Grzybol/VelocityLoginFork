package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.lang.LangConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.logging.elastic.PlayerLogContext;
import com.velocitypowered.proxy.util.AuthUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginCommand implements SimpleCommand {
    private static final Logger logger = LoggerFactory.getLogger(LoginCommand.class);
    private final AuthManager authManager;
    private final ProxyServer server;
    private final AuthConfig authConfig;
    private final LangConfig langConfig;

    public LoginCommand(AuthManager authManager, ProxyServer server, AuthConfig authConfig, LangConfig langConfig) {
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
                            authConfig.getPrefix() + "<yellow>Only players can use this command!</yellow>"
                    )
            );
            logger.warn("Login command was executed by a non-player source: {}", invocation.source());
            return;
        }

        String[] args = invocation.arguments();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    authConfig.getPrefix() + langConfig.getMessage("login-usage")
            ));
            return;
        }

        if (player.isAuthenticated() && authManager.hasValidSession(player.getUniqueId(), ip)) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("already-logged-in")
                    )
            );
            AuthUtils.sendToFirstAvailableServer(player, server, authConfig);
            PlayerLogContext.logWarn(player, "Player "+player.getUsername()+" tried to log in but is already logged in");
            return;
        }

        String password = args[0];
        if (authManager.login(player.getUniqueId(), password, ip, player.getUsername())) {
            player.setAuthenticated(true);
            PlayerLogContext.logInfo(player, "Player "+player.getUsername()+" logged in successfully");
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("login-success")
                    )
            );
            AuthUtils.sendToFirstAvailableServer(player, server, authConfig);
        } else {
            boolean justBlocked = authManager.incrementFailedAttempt(player.getUniqueId());
            if (justBlocked) {
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("blocked-login-time")
                                        + authConfig.getAttemptFailedLoginDelay() + "</bold> s.</red>"
                        )
                );
                PlayerLogContext.logWarn(player, "Player "+player.getUsername()+" was blocked for too many failed login attempts");
            } else {
                player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + langConfig.getMessage("invalid-password")
                        )
                );
                PlayerLogContext.logWarn(player, "Player "+player.getUsername()+" tried to log in with an invalid password");
            }
        }
    }
}
