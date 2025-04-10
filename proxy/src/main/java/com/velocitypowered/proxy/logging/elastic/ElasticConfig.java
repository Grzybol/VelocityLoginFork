// ElasticConfig.java
package com.velocitypowered.proxy.logging.elastic;

public class ElasticConfig {
    public boolean checkCerts = false;
    public boolean authorization = true;
    public String apiKey = "MGlnamE1UUJuQnh2OFEzWXJZeHg6WW9OeVAwZEdRelc5Q2ZheFlwamo5Zw==";
    public String webhookURL = "https://100.96.1.18";
    public int elasticsearchPort = 9200;
    public String indexPattern = "betterbox";
    public String truststorePath = "config/truststore.jks";
    public String truststorePassword = "changeit";
    public String serverName = "Velocity";
    public int flushIntervalSeconds = 60;
    public int maxBufferSize = 100;
}
