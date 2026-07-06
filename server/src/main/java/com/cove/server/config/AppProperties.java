package com.cove.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private Cors cors = new Cors();
    private Storage storage = new Storage();
    private Push push = new Push();
    private Sms sms = new Sms();
    private Email email = new Email();

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Otp getOtp() { return otp; }
    public void setOtp(Otp otp) { this.otp = otp; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Push getPush() { return push; }
    public void setPush(Push push) { this.push = push; }
    public Sms getSms() { return sms; }
    public void setSms(Sms sms) { this.sms = sms; }
    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public static class Jwt {
        private String secret;
        private String issuer;
        private long ttlMinutes = 43200;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    public static class Otp {
        private int ttlSeconds = 300;
        private int length = 6;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
    }

    public static class Cors {
        private String allowedOrigins;

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Storage {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public static class Push {
        private String vapidPublicKey;
        private String vapidPrivateKey;
        private String subject;

        public String getVapidPublicKey() { return vapidPublicKey; }
        public void setVapidPublicKey(String vapidPublicKey) { this.vapidPublicKey = vapidPublicKey; }
        public String getVapidPrivateKey() { return vapidPrivateKey; }
        public void setVapidPrivateKey(String vapidPrivateKey) { this.vapidPrivateKey = vapidPrivateKey; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
    }

    public static class Sms {
        private String provider = "mock";
        private Twilio twilio = new Twilio();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public Twilio getTwilio() { return twilio; }
        public void setTwilio(Twilio twilio) { this.twilio = twilio; }

        public static class Twilio {
            private String accountSid;
            private String authToken;
            private String fromNumber;

            public String getAccountSid() { return accountSid; }
            public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
            public String getAuthToken() { return authToken; }
            public void setAuthToken(String authToken) { this.authToken = authToken; }
            public String getFromNumber() { return fromNumber; }
            public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
        }
    }

    public static class Email {
        private String provider = "smtp";
        private String from;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }
}
