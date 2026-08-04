package it.northleap.backend.config;

import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.repositories.FieldDefRepository;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.services.PageGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
@Order(2)
public class ObjectTypeSeeder implements ApplicationRunner {

    // tabella record che contiene i vari campi
    // grazie a questa l'utente può crearsi i campi che vuole, unico problema validare noi i dati
    private record FieldSpec(String key, String label, FieldType type, boolean required,
                              List<Map<String, Object>> options, Map<String, Object> config) {
    }

    private record ObjectSpec(String key, String label, String plural, String icon, String color, List<FieldSpec> fields) {
    }

    private static FieldSpec f(String key, String label, FieldType type) {
        return new FieldSpec(key, label, type, false, null, null);
    }

    private static FieldSpec fReq(String key, String label, FieldType type) {
        return new FieldSpec(key, label, type, true, null, null);
    }

    private static FieldSpec fOpts(String key, String label, FieldType type, boolean required, List<Map<String, Object>> options) {
        return new FieldSpec(key, label, type, required, options, null);
    }

    private static FieldSpec fRelation(String key, String label, Map<String, Object> config) {
        return new FieldSpec(key, label, FieldType.RELATION, false, null, config);
    }


    // metodo che mappa chiave ed etichetta
    private static Map<String, Object> opt(String value, String label) {
        return Map.of("value", value, "label", label);
    }

    private static final List<ObjectSpec> OBJECTS = List.of(
            new ObjectSpec("company", "Azienda", "Aziende", "building", "#0A84FF", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("vat", "P. IVA", FieldType.TEXT),
                    f("website", "Sito web", FieldType.URL),
                    f("email", "Email", FieldType.EMAIL),
                    f("phone", "Telefono", FieldType.PHONE),
                    f("industry", "Settore", FieldType.TEXT),
                    f("address", "Indirizzo", FieldType.LONGTEXT)
            )),
            new ObjectSpec("contact", "Contatto", "Contatti", "user", "#2DD4FF", List.of(
                    fReq("firstName", "Nome", FieldType.TEXT),
                    f("lastName", "Cognome", FieldType.TEXT),
                    f("email", "Email", FieldType.EMAIL),
                    f("phone", "Telefono", FieldType.PHONE),
                    f("role", "Ruolo", FieldType.TEXT),
                    fOpts("type", "Tipo", FieldType.SELECT, true,
                            List.of(opt("company", "Aziendale"), opt("private", "Privato"))),
                    fRelation("company", "Azienda", Map.of("targetObject", "company", "multiple", false))
            )),
            new ObjectSpec("lead", "Lead", "Lead", "target", "#F59E0B", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("email", "Email", FieldType.EMAIL),
                    f("source", "Fonte", FieldType.TEXT),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("new", "Nuovo"), opt("contacted", "Contattato"),
                            opt("qualified", "Qualificato"), opt("lost", "Perso")))
            )),
            new ObjectSpec("pipeline", "Pipeline", "Pipeline", "filter", "#8B5CF6", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("stages", "Fasi", FieldType.JSON)
            )),
            new ObjectSpec("opportunity", "Opportunità", "Opportunità", "trending-up", "#10B981", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("amount", "Valore", FieldType.CURRENCY),
                    fOpts("stage", "Fase", FieldType.STATUS, false, List.of(
                            opt("prospect", "Prospect"), opt("proposal", "Proposta"),
                            opt("negotiation", "Negoziazione"), opt("won", "Vinta"), opt("lost", "Persa"))),
                    f("closeDate", "Chiusura prevista", FieldType.DATE)
            )),
            new ObjectSpec("quote", "Preventivo", "Preventivi", "file-text", "#0EA5E9", List.of(
                    fReq("number", "Numero", FieldType.TEXT),
                    f("total", "Totale", FieldType.CURRENCY),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("sent", "Inviato"),
                            opt("accepted", "Accettato"), opt("rejected", "Rifiutato"))),
                    f("validUntil", "Valido fino", FieldType.DATE)
            )),
            new ObjectSpec("product", "Prodotto/Servizio", "Prodotti/Servizi", "box", "#6366F1", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("sku", "SKU", FieldType.TEXT),
                    f("price", "Prezzo", FieldType.CURRENCY),
                    fOpts("kind", "Tipo", FieldType.SELECT, false,
                            List.of(opt("product", "Prodotto"), opt("service", "Servizio")))
            )),
            new ObjectSpec("contract", "Contratto", "Contratti", "file-signature", "#0891B2", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    f("value", "Valore", FieldType.CURRENCY),
                    f("startDate", "Inizio", FieldType.DATE),
                    f("endDate", "Fine", FieldType.DATE),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("active", "Attivo"), opt("expired", "Scaduto"), opt("terminated", "Risolto")))
            )),
            new ObjectSpec("project", "Progetto", "Progetti", "folder-kanban", "#14B8A6", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("planned", "Pianificato"), opt("active", "In corso"), opt("done", "Completato"))),
                    f("budget", "Budget", FieldType.CURRENCY),
                    f("deadline", "Scadenza", FieldType.DATE)
            )),
            new ObjectSpec("task", "Attività", "Attività", "check-square", "#84CC16", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("todo", "Da fare"), opt("doing", "In corso"), opt("done", "Fatto"))),
                    fOpts("priority", "Priorità", FieldType.SELECT, false, List.of(
                            opt("low", "Bassa"), opt("medium", "Media"), opt("high", "Alta"))),
                    f("dueDate", "Scadenza", FieldType.DATETIME),
                    f("assignee", "Assegnatario", FieldType.USER)
            )),
            new ObjectSpec("ticket", "Ticket", "Ticket", "life-buoy", "#EF4444", List.of(
                    fReq("subject", "Oggetto", FieldType.TEXT),
                    f("description", "Descrizione", FieldType.LONGTEXT),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("open", "Aperto"), opt("pending", "In attesa"),
                            opt("resolved", "Risolto"), opt("closed", "Chiuso"))),
                    fOpts("priority", "Priorità", FieldType.SELECT, false, List.of(
                            opt("low", "Bassa"), opt("medium", "Media"), opt("high", "Alta"), opt("urgent", "Urgente"))),
                    f("assignee", "Assegnatario", FieldType.USER)
            )),
            new ObjectSpec("document", "Documento", "Documenti", "file", "#64748B", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("file", "File", FieldType.FILE),
                    f("category", "Categoria", FieldType.TEXT)
            )),
            new ObjectSpec("digital_asset", "Asset Digitale", "Asset Digitali", "image", "#A855F7", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("file", "File", FieldType.FILE),
                    f("tags", "Tag", FieldType.MULTISELECT)
            )),
            new ObjectSpec("communication", "Comunicazione", "Comunicazioni", "message-circle", "#22D3EE", List.of(
                    f("subject", "Oggetto", FieldType.TEXT),
                    fOpts("channel", "Canale", FieldType.SELECT, false, List.of(
                            opt("email", "Email"), opt("call", "Chiamata"),
                            opt("meeting", "Meeting"), opt("whatsapp", "WhatsApp"))),
                    f("body", "Contenuto", FieldType.LONGTEXT),
                    f("date", "Data", FieldType.DATETIME)
            )),
            new ObjectSpec("calendar_event", "Evento", "Calendario", "calendar", "#3B82F6", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    fReq("start", "Inizio", FieldType.DATETIME),
                    f("end", "Fine", FieldType.DATETIME),
                    f("location", "Luogo", FieldType.TEXT),
                    f("externalId", "ID esterno (sync)", FieldType.TEXT)
            )),
            new ObjectSpec("subscription", "Abbonamento", "Abbonamenti", "repeat", "#F97316", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("amount", "Importo", FieldType.CURRENCY),
                    fOpts("interval", "Frequenza", FieldType.SELECT, false,
                            List.of(opt("monthly", "Mensile"), opt("yearly", "Annuale"))),
                    f("renewalDate", "Rinnovo", FieldType.DATE),
                    fOpts("status", "Stato", FieldType.STATUS, false,
                            List.of(opt("active", "Attivo"), opt("cancelled", "Annullato")))
            )),
            new ObjectSpec("invoice", "Fattura", "Fatture", "receipt", "#059669", List.of(
                    fReq("number", "Numero", FieldType.TEXT),
                    f("amount", "Importo", FieldType.CURRENCY),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("sent", "Inviata"),
                            opt("paid", "Pagata"), opt("overdue", "Scaduta"))),
                    f("issueDate", "Emissione", FieldType.DATE),
                    f("dueDate", "Scadenza", FieldType.DATE)
            )),
            new ObjectSpec("payment", "Pagamento", "Pagamenti", "credit-card", "#16A34A", List.of(
                    fReq("amount", "Importo", FieldType.CURRENCY),
                    f("method", "Metodo", FieldType.TEXT),
                    f("date", "Data", FieldType.DATE)
            )),
            new ObjectSpec("reminder", "Promemoria", "Reminder", "bell", "#EAB308", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    fReq("dueAt", "Quando", FieldType.DATETIME),
                    f("done", "Completato", FieldType.BOOLEAN)
            ))
    );


    // inject
    private final ObjectTypeRepository objectTypeRepository;
    private final FieldDefRepository fieldDefRepository;
    private final PageGeneratorService pageGeneratorService;

    // logger
    private static final Logger log = LoggerFactory.getLogger(ObjectTypeSeeder.class);


    // seeder idempotente: allinea il DB alla lista objects, senza mai distruggere niente
    // - ObjectType assente -> lo crea con le sue pagine
    // - ObjectType presente -> lasciato com'è (label/icona possono essere state personalizzate dall'user, il seed non deve sovrascriverle)
    // - FieldDef assente su un tipo presente -> aggiunto in coda
    // In questo modo il seed vale anche su database già popolati, che è il caso normale.
    @Override
    public void run(ApplicationArguments args) {
        for (ObjectSpec spec : OBJECTS) {
            ObjectType obj = objectTypeRepository.findByKey(spec.key()).orElse(null);
            boolean isNew = obj == null;

            if (isNew) {
                obj = new ObjectType();
                obj.setKey(spec.key());
                obj.setLabel(spec.label());
                obj.setPluralLabel(spec.plural());
                obj.setIcon(spec.icon());
                obj.setColor(spec.color());
                obj.setSystem(true);
                // in coda ai tipi già presenti, non a indice fisso: così l'ordine
                // scelto dall'utente sui tipi esistenti non si scombina
                obj.setSortOrder((int) objectTypeRepository.count());
                objectTypeRepository.save(obj);
            }


            // i campi mancanti si aggiungono anche ai tipi già esistenti
            Set<String> existingKeys = fieldDefRepository.findByObjectTypeId(obj.getId())
                    .stream().map(FieldDef::getKey).collect(Collectors.toSet());

            int fieldOrder = existingKeys.size();
            for(FieldSpec fs : spec.fields()) {
                if (existingKeys.contains(fs.key())) continue;

                FieldDef field = new FieldDef();
                field.setObjectType(obj);
                field.setKey(fs.key());
                field.setLabel(fs.label());
                field.setType(fs.type());
                field.setRequired(fs.required());
                field.setSortOrder(fieldOrder++);
                field.setOptions(fs.options());
                field.setConfig(fs.config());
                fieldDefRepository.save(field);
                log.info("seed: added field {}.{}", spec.key(), fs.key());
            }


            // le pagine si generano solo per i tipi nuovi: rigenerarle su un tipo esistente
            // sovrascriverebbe il layout che l'utente ha sistemato a mano
            if (isNew) {
                pageGeneratorService.generate(obj.getId(), true);
            }
        }
    }

}
