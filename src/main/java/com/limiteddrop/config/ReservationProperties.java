package com.limiteddrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public class ReservationProperties {
    private final Reservations reservations = new Reservations();
    private final Outbox outbox = new Outbox();
    private final Seed seed = new Seed();
    private final Security security = new Security();
    public Reservations getReservations() { return reservations; }
    public Outbox getOutbox() { return outbox; }
    public Seed getSeed() { return seed; }
    public Security getSecurity() { return security; }

    public static class Reservations {
        private Duration defaultHoldDuration = Duration.ofMinutes(10);
        private Duration expiryScanDelay = Duration.ofSeconds(5);
        private int expiryBatchSize = 100;
        public Duration getDefaultHoldDuration() { return defaultHoldDuration; }
        public void setDefaultHoldDuration(Duration defaultHoldDuration) { this.defaultHoldDuration = defaultHoldDuration; }
        public Duration getExpiryScanDelay() { return expiryScanDelay; }
        public void setExpiryScanDelay(Duration expiryScanDelay) { this.expiryScanDelay = expiryScanDelay; }
        public int getExpiryBatchSize() { return expiryBatchSize; }
        public void setExpiryBatchSize(int expiryBatchSize) { this.expiryBatchSize = expiryBatchSize; }
    }
    public static class Outbox {
        private Duration publishDelay = Duration.ofSeconds(2);
        private String exchange = "drop.events";
        public Duration getPublishDelay() { return publishDelay; }
        public void setPublishDelay(Duration publishDelay) { this.publishDelay = publishDelay; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
    }
    public static class Seed {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    public static class Security {
        private String issuerUri;
        private String jwkSetUri;
        private String hmacSecret;
        private boolean devTokenEndpointEnabled;
        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
        public String getHmacSecret() { return hmacSecret; }
        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
        public boolean isDevTokenEndpointEnabled() { return devTokenEndpointEnabled; }
        public void setDevTokenEndpointEnabled(boolean devTokenEndpointEnabled) { this.devTokenEndpointEnabled = devTokenEndpointEnabled; }
    }
}
