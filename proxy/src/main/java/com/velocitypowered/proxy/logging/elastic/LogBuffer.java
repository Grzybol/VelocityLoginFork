// LogBuffer.java
package com.velocitypowered.proxy.logging.elastic;

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

    public void add(LogEvent event) {
        String msg = event.getMessage().getFormattedMessage();
        String level = event.getLevel().toString();
        long timestamp = event.getTimeMillis();

        String ndjson = buildNdjsonChunk(timestamp, level, msg);
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

    private String buildNdjsonChunk(long timestamp, String level, String message) {
        return String.format("{\"index\":{}}\n{" +
                        "\"timestamp\":\"%d\"," +
                        "\"plugin\":\"ElasticAppender\"," +
                        "\"transactionID\":\"%s\"," +
                        "\"level\":\"%s\"," +
                        "\"message\":\"%s\"," +
                        "\"serverName\":\"%s\"}" ,
                timestamp,
                java.util.UUID.randomUUID(),
                level,
                sanitize(message),
                serverName
        );
    }

    private String sanitize(String message) {
        return message.replace("\"", "'");
    }
}
