package com.cove.server.notification;

import com.cove.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final AppProperties props;
    private final JavaMailSender mailSender;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public NotificationService(AppProperties props, JavaMailSender mailSender) {
        this.props = props;
        this.mailSender = mailSender;
    }

    public void sendEmailOtp(String email, String code) {
        if ("mock".equalsIgnoreCase(props.getEmail().getProvider())) {
            log.info("[MOCK EMAIL] OTP for {} is {}", email, code);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setFrom(props.getEmail().getFrom());
        msg.setSubject("Cove Login Code");
        msg.setText("Your Cove login code is: " + code + " (valid for 5 minutes)");
        mailSender.send(msg);
    }

    public void sendWelcomeEmail(String email) {
        if ("mock".equalsIgnoreCase(props.getEmail().getProvider())) {
            log.info("[MOCK EMAIL] Welcome email sent to {}", email);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setFrom(props.getEmail().getFrom());
        msg.setSubject("Welcome to Cove");
        msg.setText("Welcome to Cove! Your account is ready.");
        mailSender.send(msg);
    }

    public void sendSmsOtp(String phone, String code) {
        if ("mock".equalsIgnoreCase(props.getSms().getProvider())) {
            log.info("[MOCK SMS] OTP for {} is {}", phone, code);
            return;
        }
        if ("twilio".equalsIgnoreCase(props.getSms().getProvider())) {
            sendViaTwilio(phone, code);
        }
    }

    private void sendViaTwilio(String phone, String code) {
        try {
            var twilio = props.getSms().getTwilio();
            String body = "Cove code: " + code + " (valid for 5 minutes)";
            String form = "To=" + URLEncoder.encode(phone, StandardCharsets.UTF_8) +
                "&From=" + URLEncoder.encode(twilio.getFromNumber(), StandardCharsets.UTF_8) +
                "&Body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
            String auth = twilio.getAccountSid() + ":" + twilio.getAuthToken();
            String basic = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilio.getAccountSid() + "/Messages.json"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.error("Failed to send SMS via Twilio", ex);
        }
    }
}
