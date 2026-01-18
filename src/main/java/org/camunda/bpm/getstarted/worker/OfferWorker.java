package org.camunda.bpm.getstarted.worker;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.HashMap;
import java.util.Map;

public class OfferWorker {

    public static void main(String[] args) {

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(10000)
                .build();

        client.subscribe("angebotBerechnen")
                .lockDuration(20000)
                .handler(OfferWorker::handleOffer)
                .open();
    }

    private static void handleOffer(ExternalTask task, ExternalTaskService service) {

        try {
            String name            = task.getVariable("Name");
            String leistungsumfang = task.getVariable("Leistungsumfang");
            Long   anzahl          = task.getVariable("GeraeteanzahlFinal");
            Double preis           = task.getVariable("PreisProEinheit");
            Double rabattProzent   = task.getVariable("Gesamtrabatt");
            String lieferbed       = task.getVariable("Lieferbedingungen");
            Integer zahlungsziel   = task.getVariable("Zahlungsziel");
            String sonderkond      = task.getVariable("Sonderkonditionen");
            String produkt         = task.getVariable("BestellProdukt");
            String upcycling       = task.getVariable("UpcyclingOption");
            String adresse         = task.getVariable("Lieferadresse");

            if (anzahl == null) anzahl = 0L;
            if (preis == null) preis = 0.0;
            if (rabattProzent == null) rabattProzent = 0.0;
            if (lieferbed == null) lieferbed = "";
            if (sonderkond == null) sonderkond = "";
            if (produkt == null) produkt = "";
            if (upcycling == null) upcycling = "";
            if (adresse == null) adresse = "";
            if (name == null) name = "";
            if (leistungsumfang == null) leistungsumfang = "";
            if (zahlungsziel == null) zahlungsziel = 0;

            double nettoGesamt     = anzahl * preis;
            double rabattBetrag    = nettoGesamt * (rabattProzent / 100.0);
            double nettoNachRabatt = nettoGesamt - rabattBetrag;
            double angebotspreis   = Math.round(nettoNachRabatt * 100.0) / 100.0;

            StringBuilder text = new StringBuilder();
            text.append("Angebot für ").append(name).append("\n\n")
                    .append("Leistungsumfang: ").append(leistungsumfang)
                    .append(" (").append(produkt).append(")\n")
                    .append("Geräteanzahl: ").append(anzahl).append("\n")
                    .append("Upcycling-Option: ")
                    .append(!upcycling.isEmpty() ? upcycling : "keine Angabe")
                    .append("\n")
                    .append("Liefer-/Installationsadresse: ").append(adresse).append("\n\n")
                    .append("Konditionen:\n")
                    .append("- Preis pro Einheit: ").append(preis).append(" EUR\n")
                    .append("- Zwischensumme: ").append(nettoGesamt).append(" EUR\n")
                    .append("- Gesamtrabatt: ").append(rabattProzent).append(" %\n")
                    .append("- Rabattbetrag: ").append(rabattBetrag).append(" EUR\n")
                    .append("- Angebotspreis gesamt: ").append(angebotspreis).append(" EUR\n")
                    .append("- Lieferbedingungen: ").append(lieferbed).append("\n");

            if (zahlungsziel > 0) {
                text.append("- Zahlungsziel: ").append(zahlungsziel).append(" Tage\n");
            }
            if (!sonderkond.isEmpty()) {
                text.append("- Sonderkonditionen: ").append(sonderkond).append("\n");
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("Angebotspreis", angebotspreis);
            vars.put("Angebotstext", text.toString());

            service.complete(task, vars);

        } catch (Exception e) {
            service.handleFailure(
                    task,
                    "Angebot-Berechnung-Fehler",
                    e.getMessage(),
                    0,
                    0
            );
        }
    }
}
