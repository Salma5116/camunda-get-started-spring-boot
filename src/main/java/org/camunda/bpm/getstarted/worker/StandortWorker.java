package org.camunda.bpm.getstarted.worker;

import org.camunda.bpm.client.ExternalTaskClient;
import java.util.Base64;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;

public class StandortWorker {

    private static final String ORS_KEY = System.getenv("ORS_KEY");
    //Umgebungsvariable einrichten: ORS_KEY= eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImJiODE5YjAyMDYyZDRhNTZhZWRkMmI0ZmQwZjYzNzhkODFiYTg0N2NjMjM4ZjJlM2VjNmYzYzAxIiwiaCI6Im11cm11cjY0In0=

    public static void main(String[] args) {

        String user = "demo";
        String pass = "demo";
        String auth = user + ":" + pass;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        String basicAuthHeader = "Basic " + encodedAuth;

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(10000)
                .addInterceptor(new BasicAuthInterceptor(basicAuthHeader))
                .build();

        HttpClient http = HttpClient.newHttpClient();

        client.subscribe("standortBewertung")
                .lockDuration(20000)
                .handler((externalTask, externalTaskService) -> {
                    try {
                        String standort = externalTask.getVariable("Standort");
                        System.out.println("Worker hat Task geholt, Standort = " + standort);
                        if (standort == null || standort.trim().isEmpty()) {
                            throw new RuntimeException("Standort ist leer");
                        }

                        String encoded = URLEncoder.encode(standort, StandardCharsets.UTF_8);
                        String geoUrl = "https://api.openrouteservice.org/geocode/search"
                                + "?api_key=" + ORS_KEY + "&text=" + encoded;

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
                                .header("Authorization", ORS_KEY)
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
}
