package org.camunda.bpm.getstarted.worker;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.getstarted.service.GoogleCalendarService;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class KalenderLoeschWorker {

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

        client.subscribe("kalenderTerminStornieren")
                .lockDuration(20000)
                .handler((externalTask, externalTaskService) -> {
                    try {
                        String eventId = externalTask.getVariable("TerminEventId");
                        String customerName = externalTask.getVariable("Name");
                        System.out.println("Kalender-Lösch-Worker: Storniere Termin für " + customerName);

                        if (eventId == null || eventId.isEmpty()) {
                            throw new RuntimeException("TerminEventId ist leer - kann nicht löschen");
                        }

                        GoogleCalendarService calendarService = new GoogleCalendarService();
                        calendarService.deleteEvent(eventId);
                        System.out.println("Google-Event gelöscht, Event-ID: " + eventId);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("TerminGeloescht", true);
                        variables.put("LoeschZeitpunkt", new Date());

                        externalTaskService.complete(externalTask, variables);
                        System.out.println("Task erfolgreich - Termin storniert");

                    } catch (Exception e) {
                        e.printStackTrace();
                        externalTaskService.handleFailure(
                                externalTask,
                                "Kalender-Lösch-Fehler",
                                e.getMessage(),
                                0,
                                0
                        );
                    }
                })
                .open();
    }
}
