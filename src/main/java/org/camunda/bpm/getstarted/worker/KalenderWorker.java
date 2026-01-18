package org.camunda.bpm.getstarted.worker;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.getstarted.service.GoogleCalendarService;
import com.google.api.services.calendar.model.Event;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.IOException;

public class KalenderWorker {

    private static final Random random = new Random();

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

        client.subscribe("kalenderTerminErstellen")
                .lockDuration(20000)
                .handler((externalTask, externalTaskService) -> {
                    try {
                        String customerName = externalTask.getVariable("Name");
                        System.out.println("Kalender-Worker: Termin für " + customerName);

                        GoogleCalendarService calendarService = new GoogleCalendarService();
                        System.out.println("Google Calendar Service initialisiert");

                        AppointmentProposal proposal = findFreeSlot(calendarService, customerName);
                        System.out.println("Freier Termin gefunden: " + proposal.startTime);

                        String eventId = createGoogleCalendarEvent(calendarService, proposal);
                        proposal.eventId = eventId;
                        System.out.println("Google Calendar Event erstellt, Event-ID: " + eventId);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("TerminDatum", proposal.startTime + " - " + proposal.endTime);
                        variables.put("TerminEventId", eventId);
                        externalTaskService.complete(externalTask, variables);
                        System.out.println("Task erfolgreich");

                    } catch (Exception e) {
                        e.printStackTrace();
                        externalTaskService.handleFailure(externalTask, "Kalender-Fehler", e.getMessage(), 0, 0);
                    }
                })
                .open();
    }

    private static AppointmentProposal findFreeSlot(GoogleCalendarService calendarService, String customerName) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AppointmentProposal proposal = new AppointmentProposal();

        for (int attempt = 0; attempt < 100; attempt++) {
            LocalDateTime start = now
                    .plusDays(random.nextInt(14) + 1)
                    .withHour(9 + random.nextInt(8))
                    .withMinute(0)
                    .withSecond(0);

            if (start.getDayOfWeek().getValue() >= 6) continue;

            LocalDateTime end = start.plusHours(1);

            if (isSlotFree(calendarService, start, end)) {
                proposal.startTime = start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                proposal.endTime = end.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                proposal.subject = "E-Beratung für " + customerName;
                proposal.description = "Beratungstermin mit dem E-Motion Team für " + customerName;
                return proposal;
            }
        }

        throw new RuntimeException("Kein freier Slot gefunden nach 100 Versuchen");
    }

    private static boolean isSlotFree(GoogleCalendarService calendarService, LocalDateTime start, LocalDateTime end) throws IOException {
        List<Event> events = calendarService.getEventsInRange(start, end);
        System.out.println("Slot-Check: " + start + " bis " + end + " -> " + events.size() + " Events gefunden");
        return events.size() == 0;
    }

    private static String createGoogleCalendarEvent(GoogleCalendarService calendarService, AppointmentProposal proposal) throws Exception {
        LocalDateTime startDt = LocalDateTime.parse(
                proposal.startTime,
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        );
        LocalDateTime endDt = startDt.plusHours(1);

        String startISO = startDt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endISO = endDt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Event event = calendarService.createEvent(
                proposal.subject,
                proposal.description,
                startISO,
                endISO
        );

        proposal.eventLink = event.getHtmlLink();
        System.out.println("Event-Link: " + proposal.eventLink);

        return event.getId();
    }

    static class AppointmentProposal {
        String startTime;
        String endTime;
        String subject;
        String description;
        String eventId;
        String eventLink;
    }
}
