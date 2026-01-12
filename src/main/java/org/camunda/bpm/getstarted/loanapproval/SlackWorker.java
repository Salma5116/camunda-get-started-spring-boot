package org.camunda.bpm.getstarted.loanapproval;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.camunda.bpm.client.ExternalTaskClient;

public class SlackWorker {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) {

        String camundaUrl = mustGetEnv("CAMUNDA_REST_URL");  // https://.../engine-rest
        String username   = mustGetEnv("CAMUNDA_USERNAME1");
        String password   = mustGetEnv("CAMUNDA_PASSWORD1");

        String slackToken = mustGetEnv("SLACK_BOT_TOKEN");   // xoxb-...
        String slackChan  = mustGetEnv("SLACK_CHANNEL");     // C0123... oder #channel

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl(camundaUrl)
                .asyncResponseTimeout(10000)
                .addInterceptor((requestContext) -> {
                    String auth = username + ":" + password;
                    String encoded = Base64.getEncoder()
                            .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    requestContext.addHeader("Authorization", "Basic " + encoded);
                })
                .build();

        System.out.println("✅ SlackWorker läuft. Warte auf External Tasks Topic: slackNotify");

        client.subscribe("slackNotify")
                .lockDuration(20000)
                .handler((task, service) -> {
                    sendSlackMessage(slackToken, slackChan,
                            "✅ External Task 'slackNotify' wurde verarbeitet!");
                    service.complete(task);
                })
                .open();
    }

    private static void sendSlackMessage(String token, String channel, String text) {
        try {
            String body = "{\"channel\":\"" + escapeJson(channel) + "\",\"text\":\"" + escapeJson(text) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://slack.com/api/chat.postMessage"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Slack HTTP " + resp.statusCode() + ": " + resp.body());
            }
            if (!resp.body().contains("\"ok\":true")) {
                throw new RuntimeException("Slack API error: " + resp.body());
            }

            System.out.println("📨 Slack Nachricht gesendet.");

        } catch (Exception e) {
            throw new RuntimeException("Slack message failed", e);
        }
    }

    private static String escapeJson(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String mustGetEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Fehlende Umgebungsvariable: " + name);
        }
        return v;
    }
}
