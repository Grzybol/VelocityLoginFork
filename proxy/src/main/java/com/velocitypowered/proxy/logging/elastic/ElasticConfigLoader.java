// ElasticConfigLoader.java
package com.velocitypowered.proxy.logging.elastic;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;

import java.io.File;

public class ElasticConfigLoader {
    public static ElasticConfig loadFromFile(File baseDirectory) {
        File configFile = new File(baseDirectory, "elasticConfig.toml");
        ElasticConfig config = new ElasticConfig();

        if (!configFile.exists()) {
            CommentedFileConfig newFile = CommentedFileConfig.of(configFile, TomlFormat.instance());
            newFile.set("checkCerts", config.checkCerts);
            newFile.set("authorization", config.authorization);
            newFile.set("apiKey", config.apiKey);
            newFile.set("webhookURL", config.webhookURL);
            newFile.set("elasticsearchPort", config.elasticsearchPort);
            newFile.set("indexPattern", config.indexPattern);
            newFile.set("truststorePath", config.truststorePath);
            newFile.set("truststorePassword", config.truststorePassword);
            newFile.set("serverName", config.serverName);
            newFile.set("flushIntervalSeconds", config.flushIntervalSeconds);
            newFile.set("maxBufferSize", config.maxBufferSize);
            newFile.save();
            newFile.close();
            return config;
        }

        try (CommentedFileConfig cfg = CommentedFileConfig.of(configFile, TomlFormat.instance())) {
            cfg.load();
            config.checkCerts = cfg.getOrElse("checkCerts", config.checkCerts);
            config.authorization = cfg.getOrElse("authorization", config.authorization);
            config.apiKey = cfg.getOrElse("apiKey", config.apiKey);
            config.webhookURL = cfg.getOrElse("webhookURL", config.webhookURL);
            config.elasticsearchPort = cfg.getOrElse("elasticsearchPort", config.elasticsearchPort);
            config.indexPattern = cfg.getOrElse("indexPattern", config.indexPattern);
            config.truststorePath = cfg.getOrElse("truststorePath", config.truststorePath);
            config.truststorePassword = cfg.getOrElse("truststorePassword", config.truststorePassword);
            config.serverName = cfg.getOrElse("serverName", config.serverName);
            config.flushIntervalSeconds = cfg.getOrElse("flushIntervalSeconds", config.flushIntervalSeconds);
            config.maxBufferSize = cfg.getOrElse("maxBufferSize", config.maxBufferSize);
        }

        return config;
    }
}
