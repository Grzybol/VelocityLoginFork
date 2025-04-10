package com.velocitypowered.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.proxy.auth.AuthManager;
import com.velocitypowered.proxy.config.AuthConfig;
import com.velocitypowered.proxy.lang.LangConfig;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangePasswordCommand implements SimpleCommand {
    private static final Logger logger = LoggerFactory.getLogger(ChangePasswordCommand.class);
    private final AuthManager authManager;
    private final AuthConfig authConfig;
    private final LangConfig langConfig;

    public ChangePasswordCommand(AuthManager authManager, AuthConfig authConfig, LangConfig langConfig) {
        this.authManager = authManager;
        this.authConfig = authConfig;
        this.langConfig = langConfig;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (invocation.source() instanceof ConnectedPlayer player) {
            if (args.length != 3) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("changepassword-usage")
                ));
                return;
            }

            String oldPass = args[0], newPass1 = args[1], newPass2 = args[2];

            if (!newPass1.equals(newPass2)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("passwords-dont-match")
                ));
                return;
            }

            if (!authManager.isValidPassword(newPass1)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("password-dont-meet-exp")
                ));
                return;
            }

            if (authManager.updatePassword(player.getUniqueId(), oldPass, newPass1)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("password-changed")
                ));
                logger.info("Gracz {} zmienił hasło", player.getUsername());
            } else {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("invalid-password")
                ));
            }

        } else if (invocation.source() instanceof ConsoleCommandSource) {
            if (args.length != 2) {
                invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("changepassword-console-usage")
                ));
                return;
            }

            if (!authManager.isValidPassword(args[1])) {
                invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("password-dont-meet-exp")
                ));
                return;
            }

            if (authManager.updatePassword(args[0], args[1])) {
                invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("password-changed")
                ));
                logger.info("Hasło gracza {} zostało zmienione z poziomu konsoli", args[0]);
            } else {
                invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                        authConfig.getPrefix() + langConfig.getMessage("invalid-password")
                ));
            }
        }
    }
}
