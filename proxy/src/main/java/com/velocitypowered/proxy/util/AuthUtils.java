package com.velocitypowered.proxy.util;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.config.AuthConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;

public class AuthUtils {

    public static void sendToFirstAvailableServer(ConnectedPlayer player, ProxyServer server, AuthConfig authConfig) {
        Optional<RegisteredServer> targetServer = authConfig.getAuthServers().stream()
                .filter(serverName -> !serverName.equalsIgnoreCase(authConfig.getAuthServer()))
                .map(server::getServer)
                .flatMap(Optional::stream)
                .findFirst();

        targetServer.ifPresentOrElse(
                srv -> player.createConnectionRequest(srv).fireAndForget(),
                () -> player.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                                authConfig.getPrefix() + "<red>Brak dostępnych serwerów docelowych.</red>"
                        )
                )
        );
    }
}
