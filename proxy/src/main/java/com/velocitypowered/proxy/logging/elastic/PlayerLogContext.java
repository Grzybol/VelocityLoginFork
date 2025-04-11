// PlayerLogContext.java
package com.velocitypowered.proxy.logging.elastic;

import com.velocitypowered.proxy.connection.client.ConnectedPlayer;

public class PlayerLogContext {

    private static LogBuffer logBuffer;

    // Rejestrujemy bufor logów (np. w VelocityServer)
    public static void setLogBuffer(LogBuffer buffer) {
        logBuffer = buffer;
    }

    // Główna metoda do logowania z danymi gracza
    public static void logInfo(ConnectedPlayer player, String message) {
        log(player, message, "INFO");
    }

    public static void logWarn(ConnectedPlayer player, String message) {
        log(player, message, "WARN");
    }

    public static void logError(ConnectedPlayer player, String message) {
        log(player, message, "ERROR");
    }

    private static void log(ConnectedPlayer player, String message, String level) {
        if (logBuffer == null) return;

        String playerName = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String connectedServer = player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("UNKNOWN");

        logBuffer.add(message, level, playerName, ip, connectedServer);
    }
}
