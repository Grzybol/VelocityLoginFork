// ElasticSender.java
package com.velocitypowered.proxy.logging.elastic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

public class ElasticSender {
    private static final Logger logger = LogManager.getLogger(ElasticSender.class);

    private final ElasticConfig config;

    public ElasticSender(ElasticConfig config) {
        this.config = config;
        setupSSL();
    }

    private void setupSSL() {
        if (!config.checkCerts) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return null; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                logger.error("Error setting up SSL context: {}", e.getMessage());
            }
        } else {
            try {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (InputStream trustStoreIS = new FileInputStream(config.truststorePath)) {
                    trustStore.load(trustStoreIS, config.truststorePassword.toCharArray());
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            } catch (Exception e) {
                logger.error("Failed to set SSL properties: {}", e.getMessage());
            }
        }
    }

    public boolean sendLogs(List<String> ndjsonPayload) {
        try {
            String elasticUrl = config.webhookURL + ":" + config.elasticsearchPort + "/" + config.indexPattern + "/_bulk";
            logger.info("Sending {} log(s) to Elasticsearch at {}", ndjsonPayload.size(), elasticUrl);

            URL url = new URL(elasticUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-ndjson; charset=UTF-8");
            if (config.authorization) {
                conn.setRequestProperty("Authorization", "ApiKey " + config.apiKey);
            }
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                for (String line : ndjsonPayload) {
                    os.write(line.getBytes(StandardCharsets.UTF_8));
                    os.write('\n');
                }
            }

            int responseCode = conn.getResponseCode();
            String responseMsg = conn.getResponseMessage();
            logger.info("Elasticsearch response: {} - {}", responseCode, responseMsg);
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            logger.error("Failed to send logs to Elasticsearch: {}", e.getMessage());
            return false;
        }
    }
}
