// LogBuffer.java
package com.velocitypowered.proxy.logging.elastic;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LogBuffer {
    private final List<String> ndjsonBuffer = Collections.synchronizedList(new ArrayList<>());
    private final ElasticSender sender;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int flushIntervalSeconds;
    private final int maxBufferSize;
    private final String serverName;


    public LogBuffer(ElasticSender sender, ElasticConfig config) {
        this.sender = sender;
        this.flushIntervalSeconds = config.flushIntervalSeconds;
        this.maxBufferSize = config.maxBufferSize;
        this.serverName = config.serverName;

        scheduler.scheduleAtFixedRate(this::flush, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
    }
    public void addInfo(String message, String playerName, String ip, String serverName) {
        long timestamp = System.currentTimeMillis();
        String level = "INFO";

        String ndjson = buildNdjsonChunk(timestamp, level, message, playerName, ip, serverName);
        ndjsonBuffer.add(ndjson);

        if (ndjsonBuffer.size() >= maxBufferSize) {
            flush();
        }
    }


    public void add(LogEvent event) {
        String msg = event.getMessage().getFormattedMessage();
        String level = event.getLevel().toString();
        long timestamp = event.getTimeMillis();

        String playerName = ThreadContext.get("playerName");
        String ip = ThreadContext.get("ip");
        String connectedServer = ThreadContext.get("connectedServer");

        String ndjson = buildNdjsonChunk(timestamp, level, msg, playerName, ip, connectedServer);
        ndjsonBuffer.add(ndjson);

        if (ndjsonBuffer.size() >= maxBufferSize) {
            flush();
        }
    }
    public void add(String message, String level, String playerName, String ip, String connectedServer) {
        long timestamp = System.currentTimeMillis();

        String ndjson = buildNdjsonChunk(timestamp, level, message, playerName, ip, connectedServer);
        ndjsonBuffer.add(ndjson);

        if (ndjsonBuffer.size() >= maxBufferSize) {
            flush();
        }
    }

    public void flush() {
        List<String> toSend;
        synchronized (ndjsonBuffer) {
            if (ndjsonBuffer.isEmpty()) return;
            toSend = new ArrayList<>(ndjsonBuffer);
            ndjsonBuffer.clear();
        }
        sender.sendLogs(toSend);
    }

    private String buildNdjsonChunk(long timestamp, String level, String message, String playerName, String ip, String connectedServer) {
        StringBuilder json = new StringBuilder();
        json.append("{\"index\":{}}\n{");
        json.append(String.format("\"timestamp\":\"%d\",", timestamp));
        json.append(String.format("\"plugin\":\"ElasticAppender\","));
        json.append(String.format("\"transactionID\":\"%s\",", java.util.UUID.randomUUID()));
        json.append(String.format("\"level\":\"%s\",", level));
        json.append(String.format("\"message\":\"%s\",", sanitize(message)));
        json.append(String.format("\"serverName\":\"%s\"", sanitize(serverName)));

        if (playerName != null) {
            json.append(String.format(",\"playerName\":\"%s\"", sanitize(playerName)));
        }
        if (ip != null) {
            json.append(String.format(",\"ip\":\"%s\"", sanitize(ip)));
        }
        if (connectedServer != null) {
            json.append(String.format(",\"connectedServer\":\"%s\"", sanitize(connectedServer)));
        }


        json.append("}\n");
        return json.toString();
    }

    private String sanitize(String message) {
        return message.replace("\"", "'").replace("\n", " ");
    }
}
