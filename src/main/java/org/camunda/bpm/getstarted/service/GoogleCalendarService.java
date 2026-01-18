package org.camunda.bpm.getstarted.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.*;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "Emotion Calendar Worker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private Calendar service;

    public GoogleCalendarService() throws GeneralSecurityException, IOException {
        this.service = buildCalendarService();
    }

    private Calendar buildCalendarService() throws GeneralSecurityException, IOException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Calendar.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential getCredentials(com.google.api.client.http.HttpTransport httpTransport)
            throws IOException {
        InputStream in = GoogleCalendarService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR)
        )
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public Event createEvent(String title, String description, String startTime, String endTime)
            throws IOException {
        Event event = new Event()
                .setSummary(title)
                .setDescription(description);

        EventDateTime start = new EventDateTime()
                .setDateTime(DateTime.parseRfc3339(startTime))
                .setTimeZone("Europe/Berlin");
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(DateTime.parseRfc3339(endTime))
                .setTimeZone("Europe/Berlin");
        event.setEnd(end);

        Event createdEvent = service.events().insert("primary", event).execute();
        System.out.println("Event created: " + createdEvent.getHtmlLink());
        return createdEvent;
    }

    public void deleteEvent(String eventId) throws IOException {
        service.events().delete("primary", eventId).execute();
        System.out.println("Event deleted: " + eventId);
    }

    public List<Event> getEventsInRange(LocalDateTime start, LocalDateTime end) throws IOException {
        DateTime startDateTime = new DateTime(java.sql.Timestamp.valueOf(start).getTime());
        DateTime endDateTime = new DateTime(java.sql.Timestamp.valueOf(end).getTime());

        Events events = service.events().list("primary")
                .setTimeMin(startDateTime)
                .setTimeMax(endDateTime)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        List<Event> items = events.getItems();
        System.out.println("Events in range [" + start + " to " + end + "]: " + items.size());
        return items != null ? items : Collections.emptyList();
    }

    public List<Event> listUpcomingEvents(int maxResults) throws IOException {
        Events events = service.events().list("primary")
                .setMaxResults(maxResults)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        return events.getItems();
    }

    public Calendar getService() {
        return service;
    }
}
