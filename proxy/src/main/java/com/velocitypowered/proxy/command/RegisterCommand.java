package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.lang.LangConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterCommand implements SimpleCommand {
    private static final Logger logger = LoggerFactory.getLogger(RegisterCommand.class);
    private final AuthManager authManager;
    private final ProxyServer server;
    private final AuthConfig authConfig;
    private final LangConfig langConfig;

    public RegisterCommand(AuthManager authManager, ProxyServer server, AuthConfig authConfig, LangConfig langConfig) {
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
            return;
        }

        String[] args = invocation.arguments();
        if (args.length != 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    authConfig.getPrefix() + langConfig.getMessage("register-usage")
            ));
            return;
        }

        String pass1 = args[0];
        String pass2 = args[1];
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

        if(authManager.getNumberOfAccounts(ip) >= authConfig.getMaxAccountsPerIp()) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("max-accounts-reached")
                    )
            );
            return;
        }

        if (authManager.register(player.getUniqueId(), pass1, ip, player.getUsername())) {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("register-success")
                    )
            );
            Component kickMessage = MiniMessage.miniMessage().deserialize(
                    authConfig.getPrefix() + "<newline>" + langConfig.getMessage("register-success") + "<newline><yellow>re-join to login</yellow>"
            );
            logger.info("Gracz {} zarejestrował się z IP {} i został rozłączony", player.getUsername(), ip);
            player.disconnect(kickMessage);
        } else {
            player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                            authConfig.getPrefix() + langConfig.getMessage("already-registered")
                    )
            );
        }
    }
}
