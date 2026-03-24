package com.ldapadmin.service.siem;

import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.enums.SiemProtocol;
import com.ldapadmin.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Transport layer for delivering formatted audit events to SIEM targets.
 * Supports UDP syslog, TCP syslog, and HTTPS webhook.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SiemClient {

    private final EncryptionService encryptionService;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Sends a formatted message to the configured SIEM target.
     *
     * @param settings the current application settings (contains SIEM config)
     * @param message  the pre-formatted event string
     */
    public void send(ApplicationSettings settings, String message) {
        SiemProtocol protocol = settings.getSiemProtocol();
        if (protocol == null) {
            log.warn("SIEM protocol not configured, skipping export");
            return;
        }

        switch (protocol) {
            case SYSLOG_UDP -> sendUdp(settings.getSiemHost(), settings.getSiemPort(), message);
            case SYSLOG_TCP -> sendTcp(settings.getSiemHost(), settings.getSiemPort(), message);
            case WEBHOOK    -> sendWebhook(settings, message);
        }
    }

    private void sendUdp(String host, Integer port, String message) {
        int targetPort = port != null ? port : 514;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, targetPort);
            socket.send(packet);
        } catch (Exception e) {
            log.error("Failed to send syslog UDP to {}:{}: {}", host, targetPort, e.getMessage());
        }
    }

    private void sendTcp(String host, Integer port, String message) {
        int targetPort = port != null ? port : 514;
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, targetPort),
                    (int) CONNECT_TIMEOUT.toMillis());
            socket.setSoTimeout((int) REQUEST_TIMEOUT.toMillis());
            OutputStream out = socket.getOutputStream();
            // RFC 6587 octet-counting framing
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            String frame = data.length + " ";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.flush();
        } catch (Exception e) {
            log.error("Failed to send syslog TCP to {}:{}: {}", host, targetPort, e.getMessage());
        }
    }

    private void sendWebhook(ApplicationSettings settings, String message) {
        String url = settings.getWebhookUrl();
        if (url == null || url.isBlank()) {
            log.warn("Webhook URL not configured, skipping");
            return;
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8));

            // Add Authorization header if configured
            if (settings.getWebhookAuthHeaderEnc() != null) {
                String authHeader = encryptionService.decrypt(settings.getWebhookAuthHeaderEnc());
                reqBuilder.header("Authorization", authHeader);
            }

            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build()) {
                HttpResponse<String> resp = client.send(reqBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    log.error("Webhook returned HTTP {}: {}", resp.statusCode(), resp.body());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send webhook to {}: {}", url, e.getMessage());
        }
    }

    /**
     * Tests connectivity to the configured SIEM target.
     * Returns a human-readable result message.
     */
    public String testConnectivity(ApplicationSettings settings) {
        SiemProtocol protocol = settings.getSiemProtocol();
        if (protocol == null) return "No SIEM protocol configured.";

        try {
            switch (protocol) {
                case SYSLOG_UDP -> {
                    InetAddress.getByName(settings.getSiemHost());
                    return "UDP: DNS resolved " + settings.getSiemHost() + " successfully. "
                            + "Note: UDP delivery cannot be confirmed.";
                }
                case SYSLOG_TCP -> {
                    int port = settings.getSiemPort() != null ? settings.getSiemPort() : 514;
                    try (Socket socket = new Socket()) {
                        socket.connect(new java.net.InetSocketAddress(settings.getSiemHost(), port),
                                (int) CONNECT_TIMEOUT.toMillis());
                    }
                    return "TCP: Connected to " + settings.getSiemHost() + ":" + port + " successfully.";
                }
                case WEBHOOK -> {
                    String url = settings.getWebhookUrl();
                    if (url == null || url.isBlank()) return "Webhook URL not configured.";
                    // Send an empty test via OPTIONS/HEAD or a test payload
                    return "Webhook: URL " + url + " configured. Send a test event to verify delivery.";
                }
            }
        } catch (Exception e) {
            return "Connection test failed: " + e.getMessage();
        }
        return "Unknown protocol.";
    }
}
