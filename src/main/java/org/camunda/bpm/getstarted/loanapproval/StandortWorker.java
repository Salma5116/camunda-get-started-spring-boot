package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.client.ExternalTaskClient;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class StandortWorker {

    public static void main(String[] args) {

        String camundaUrl = mustGetEnv("CAMUNDA_REST_URL");     // z.B. https://.../engine-rest
        String username   = mustGetEnv("CAMUNDA_USERNAME");     // z.B. 05
        String password   = mustGetEnv("CAMUNDA_PASSWORD");     // z.B. HTWberlin1.
        String orsKey     = mustGetEnv("ORS_KEY");              // OpenRouteService Key

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

        HttpClient http = HttpClient.newHttpClient();

        client.subscribe("standortBewertung")
                .lockDuration(20000)
                .handler((externalTask, externalTaskService) -> {
                    try {
                        String standort = (String) externalTask.getVariable("Standort");
                        System.out.println(">>> Worker hat Task geholt, Standort = " + standort);

                        if (standort == null || standort.trim().isEmpty()) {
                            throw new RuntimeException("Standort ist leer");
                        }

                        String encoded = URLEncoder.encode(standort, StandardCharsets.UTF_8);
                        String geoUrl = "https://api.openrouteservice.org/geocode/search"
                                + "?api_key=" + orsKey + "&text=" + encoded;

                        HttpRequest geoReq = HttpRequest.newBuilder()
                                .uri(URI.create(geoUrl))
                                .GET()
                                .build();

                        HttpResponse<String> geoResp = http.send(geoReq, HttpResponse.BodyHandlers.ofString());
                        String geoJsonText = geoResp.body();
                        System.out.println("Geocode-Response: " + geoJsonText);

                        JSONObject geoRoot = new JSONObject(geoJsonText);
                        JSONArray features = geoRoot.getJSONArray("features");
                        if (features.isEmpty()) {
                            throw new RuntimeException("Geocoding liefert 0 Treffer für: " + standort);
                        }

                        JSONObject first = features.getJSONObject(0);
                        JSONArray coords = first
                                .getJSONObject("geometry")
                                .getJSONArray("coordinates");
                        double destLon = coords.getDouble(0);
                        double destLat = coords.getDouble(1);
                        System.out.println("Geocode-Koordinate: " + destLon + "," + destLat);

                        // Ursprung (Berlin)
                        double originLon = 13.4050;
                        double originLat = 52.5200;

                        JSONObject body = new JSONObject()
                                .put("locations", new JSONArray()
                                        .put(new JSONArray().put(originLon).put(originLat))
                                        .put(new JSONArray().put(destLon).put(destLat)))
                                .put("metrics", new JSONArray().put("distance").put("duration"))
                                .put("units", "km");

                        HttpRequest matrixReq = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.openrouteservice.org/v2/matrix/driving-car"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", orsKey)
                                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                .build();

                        HttpResponse<String> matrixResp = http.send(matrixReq, HttpResponse.BodyHandlers.ofString());
                        String matrixText = matrixResp.body();
                        System.out.println("Matrix-Response: " + matrixText);

                        JSONObject matrixJson = new JSONObject(matrixText);

                        if (!matrixJson.has("distances") || !matrixJson.has("durations")) {
                            throw new RuntimeException("Matrix liefert keine distances/durations");
                        }

                        JSONArray distances = matrixJson.getJSONArray("distances");
                        JSONArray durations = matrixJson.getJSONArray("durations");

                        double distKm = distances.getJSONArray(0).getDouble(1);
                        double durationSec = durations.getJSONArray(0).getDouble(1);
                        double durationMin = durationSec / 60.0;

                        externalTaskService.complete(externalTask, Map.of(
                                "DistanzKm", distKm,
                                "FahrzeitMin", durationMin
                        ));

                    } catch (Exception e) {
                        e.printStackTrace();
                        externalTaskService.handleFailure(
                                externalTask,
                                "ORS Fehler",
                                e.getMessage(),
                                0,
                                0
                        );
                    }
                })
                .open();
    }

    private static String mustGetEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Fehlende Umgebungsvariable: " + name);
        }
        return v;
    }
}
