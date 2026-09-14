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

    // numerazione automatica del modulo: null = nessuna (il caso di quasi tutti i tipi)
    private record NumberingSpec(String field, String prefix, boolean useYear, int padding, String separator) {
    }

    private record ObjectSpec(String key, String label, String plural, String icon, String color,
                              List<FieldSpec> fields, NumberingSpec numbering) {
        // i tipi senza numerazione restano scritti con sei argomenti
        ObjectSpec(String key, String label, String plural, String icon, String color, List<FieldSpec> fields) {
            this(key, label, plural, icon, color, fields, null);
        }
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
                    // NON obbligatorio: lo riempie la numerazione automatica, che gira dopo la
                    // validazione. Required + valore generato dal sistema non stanno insieme
                    f("number", "Numero", FieldType.TEXT),
                    f("total", "Totale", FieldType.CURRENCY),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("sent", "Inviato"),
                            opt("accepted", "Accettato"), opt("rejected", "Rifiutato"))),
                    f("validUntil", "Valido fino", FieldType.DATE),
                    f("subject", "Soggetto", FieldType.TEXT),
                    fRelation("company", "Nome azienda", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("opportunity", "Nome opportunità", Map.of("targetObject", "opportunity", "multiple", false)),
                    fRelation("contact", "Nome contatto", Map.of("targetObject", "contact", "multiple", false)),
                    fRelation("paymentMethod", "Modalità di pagamento", Map.of("targetObject", "payment_method", "multiple", false)),

                    // meccanismo revisioni, il figlio punta al padre
                    fRelation("previousVersion", "Versione precedente", Map.of("targetObject", "quote", "multiple", false)),
                    f("quoteDate", "Data preventivo", FieldType.DATE),
                    f("subtotal", "Totale parziale", FieldType.CURRENCY),
                    f("discountTotal", "Sconto", FieldType.CURRENCY),
                    f("shippingCost", "Spese di trasporto", FieldType.CURRENCY),
                    f("vatTotal", "IVA", FieldType.CURRENCY),
                    f("notes", "Note", FieldType.LONGTEXT),
                    f("paymentTerms", "Condizioni di pagamento", FieldType.TEXT),
                    f("billingAddress", "Indirizzo di fatturazione", FieldType.LONGTEXT),
                    f("shippingAddress", "Indirizzo di spedizione", FieldType.LONGTEXT)
            ), new NumberingSpec("number", null, true, 0, "-")),
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
                    f("subject", "Oggetto", FieldType.TEXT),
                    // numerato "TT643": il titolo resta comunque il soggetto, perché in
                    // TITLE_KEYS subject viene prima di number
                    f("number", "Numero", FieldType.TEXT),
                    f("description", "Descrizione", FieldType.LONGTEXT),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("open", "Aperto"), opt("pending", "In attesa"),
                            opt("resolved", "Risolto"), opt("closed", "Chiuso"))),
                    fOpts("priority", "Priorità", FieldType.SELECT, false, List.of(
                            opt("low", "Bassa"), opt("medium", "Media"), opt("high", "Alta"), opt("urgent", "Urgente"))),
                    f("assignee", "Assegnatario", FieldType.USER)
            ), new NumberingSpec("number", "TT", false, 0, "-")),
            new ObjectSpec("document", "Documento", "Documenti", "file", "#64748B", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("file", "File", FieldType.FILE),
                    f("category", "Categoria", FieldType.TEXT)
            )),
            new ObjectSpec("digital_asset", "Asset digitale", "Asset digitali", "image", "#A855F7", List.of(
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
            new ObjectSpec("calendar_event", "Evento", "Eventi", "calendar", "#3B82F6", List.of(
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
                    // vedi quote: la riempie la numerazione automatica
                    f("number", "Numero", FieldType.TEXT),
                    f("amount", "Importo", FieldType.CURRENCY),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("sent", "Inviata"),
                            opt("paid", "Pagata"), opt("overdue", "Scaduta"))),
                    f("issueDate", "Emissione", FieldType.DATE),
                    f("dueDate", "Scadenza", FieldType.DATE),
                    fRelation("company", "Nome azienda", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("salesOrder", "Ordine", Map.of("targetObject", "sales_order", "multiple", false)),
                    fRelation("paymentMethod", "Modalità di pagamento", Map.of("targetObject", "payment_method", "multiple", false)),
                    f("shippingCost", "Spese di trasporto", FieldType.CURRENCY),
                    f("subtotal", "Imponibile", FieldType.CURRENCY),
                    // senza questi due RecordValidator li butta via al primo salvataggio dalla UI:
                    // tiene solo le chiavi dichiarate, e writeTotals invece li scrive
                    f("discountTotal", "Sconto", FieldType.CURRENCY),
                    f("total", "Totale", FieldType.CURRENCY),
                    f("vatTotal", "IVA", FieldType.CURRENCY),
                    f("notes", "Note", FieldType.LONGTEXT),
                    f("paymentTerms", "Condizioni di pagamento", FieldType.TEXT),
                    f("billingAddress", "Indirizzo di fatturazione", FieldType.LONGTEXT)
            ), new NumberingSpec("number", "FT", true, 4, "-")),
            new ObjectSpec("payment", "Pagamento", "Pagamenti", "credit-card", "#16A34A", List.of(
                    fReq("amount", "Importo", FieldType.CURRENCY),
                    f("method", "Metodo", FieldType.TEXT),
                    f("date", "Data", FieldType.DATE)
            )),
            new ObjectSpec("reminder", "Promemoria", "Reminder", "bell", "#EAB308", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    fReq("dueAt", "Quando", FieldType.DATETIME),
                    f("done", "Completato", FieldType.BOOLEAN)
            )),
            new ObjectSpec("supplier", "Fornitore", "Fornitori", "truck", "#78716C", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("vat", "P. IVA", FieldType.TEXT),
                    f("email", "Email", FieldType.EMAIL),
                    f("phone", "Telefono", FieldType.PHONE),
                    f("website", "Sito web", FieldType.URL),
                    f("address", "Indirizzo", FieldType.LONGTEXT),
                    f("notes", "Note", FieldType.LONGTEXT)
            )),
            new ObjectSpec("sales_order", "Ordine di vendita", "Ordini di vendita", "clipboard-list", "#0284C7", List.of(
                    f("subject", "Soggetto", FieldType.TEXT),
                    f("number", "Ordine n.", FieldType.TEXT),
                    fRelation("company", "Nome Azienda", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("contact", "Nome Contatto", Map.of("targetObject", "contact", "multiple", false)),
                    // link della conversione: da quale preventivo nasce quest'ordine
                    fRelation("quote", "Nome preventivo", Map.of("targetObject", "quote", "multiple", false)),
                    fOpts("status", "Stato", FieldType.STATUS, true, List.of(
                        opt("created", "Creato"), opt("confirmed", "Confermato"),
                        opt("shipped", "Spedito"), opt("closed", "Chiuso"),
                        opt("cancelled", "Annullato"))),
                    f("orderDate", "Data ordine", FieldType.DATE),
                    fRelation("paymentMethod", "Modalità di pagamento", Map.of("targetObject", "payment_method", "multiple", false)),
                    // totali persistiti, ricalcolati dal service delle righe
                    // devono essere colonne di lista filtrabili e ordinabili
                    f("subtotal", "Sub totale", FieldType.CURRENCY),
                    f("shippingCost", "Spese di trasporto", FieldType.CURRENCY),
                    f("discountTotal", "Sconto", FieldType.CURRENCY),
                    f("vatTotal", "IVA", FieldType.CURRENCY),
                    f("total", "Totale", FieldType.CURRENCY),
                    f("notes", "Note", FieldType.LONGTEXT),
                    f("paymentTerms", "Condizioni di pagamento", FieldType.TEXT),
                    f("billingAddress", "Indirizzo di fatturazione", FieldType.LONGTEXT),
                    f("shippingAddress", "Indirizzo di spedizione", FieldType.LONGTEXT)
            )),
            new ObjectSpec("delivery_note", "DDT", "DDT", "package-check", "#7C3AED", List.of(
                    f("subject", "Soggetto", FieldType.TEXT),
                    f("number", "DDT n.", FieldType.TEXT),
                    fRelation("company", "Nome Azienda", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("salesOrder", "Ordine di riferimento", Map.of("targetObject", "sales_order", "multiple", false)),
                    f("date", "Data", FieldType.DATE),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("shipped", "Spedito"), opt("delivered", "Consegnato"))),
                    f("carrier", "Vettore", FieldType.TEXT),
                    f("shippingCost", "Spese di trasporto", FieldType.CURRENCY),
                    f("packages", "Colli", FieldType.INTEGER),
                    f("weight", "Peso (kg)", FieldType.DECIMAL),
                    f("notes", "Note", FieldType.LONGTEXT),
                    f("paymentTerms", "Condizioni di pagamento", FieldType.TEXT),
                    f("shippingAddress", "Indirizzo di spedizione", FieldType.LONGTEXT),
                    f("reason", "Causale trasporto", FieldType.TEXT)
            )),
            new ObjectSpec("visit_report", "Report visita", "Report visite", "map-pin", "#D97706", List.of(
                    f("subject", "Oggetto", FieldType.TEXT),
                    fRelation("company", "Azienda", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("contact", "Contatto", Map.of("targetObject", "contact", "multiple", false)),
                    f("date", "Data visita", FieldType.DATE),
                    f("outcome", "Esito", FieldType.LONGTEXT),
                    f("nextStep", "Prossimo passo", FieldType.TEXT),
                    f("assignee", "Assegnato a", FieldType.USER)
            )),

            // INVENTARIO, tabelle di configurazione
            new ObjectSpec("vat_rate", "Aliquota IVA", "Aliquote IVA", "percent", "#DC2626", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    fReq("rate", "Aliquota %", FieldType.PERCENT),
                    // per le operazioni non imponibili/esenti serve la natura ai fini di SDI
                    f("sdiNature", "Natura SDI", FieldType.TEXT),
                    f("isDefault", "Predefinita", FieldType.BOOLEAN)
            )),
            new ObjectSpec("payment_method", "Modalità di pagamento", "Modalità di pagamento", "wallet", "#0F766E", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("days", "Giorni", FieldType.INTEGER),
                    f("sdiCode", "Codice SDI", FieldType.TEXT),
                    f("notes", "Note", FieldType.LONGTEXT)
            )),
            new ObjectSpec("price_book", "Listino", "Listini", "list-ordered", "#4F46E5", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("currency", "Valuta", FieldType.TEXT),
                    f("validFrom", "Valido da", FieldType.DATE),
                    f("validTo", "Valido fino a", FieldType.DATE),
                    f("isDefault", "Predefinito", FieldType.BOOLEAN)
            )),
            // riga di listino: prezzo di un articolo dentro un listino
            // è una tabella ponte, ma i suoi campi sono tutti scalari (regge bene come RECORD)
            new ObjectSpec("price_book_item", "Riga di listino", "Righe di listino", "tag", "#4338CA", List.of(
                    fRelation("priceBook", "Listino", Map.of("targetObject", "price_book", "multiple", false)),
                    fRelation("product", "Articolo", Map.of("targetObject", "product", "multiple", false)),
                    fReq("price", "Prezzo", FieldType.CURRENCY),
                    f("minQuantity", "Quantità minima", FieldType.DECIMAL)
            )),

            // supporto
            new ObjectSpec("service_contract", "Contratto di servizio", "Contratti di servizio", "shield-check", "#0369A1", List.of(
                    f("subject", "Oggetto", FieldType.TEXT),
                    fRelation("company", "Azienda", Map.of("targetObject", "company", "multiple", false)),
                    f("startDate", "Inizio", FieldType.DATE),
                    f("endDate", "Fine", FieldType.DATE),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("active", "In corso"), opt("expiring", "In scadenza"),
                            opt("expired", "Scaduto"), opt("terminated", "Risolto"))),
                    fOpts("slaType", "Tipo SLA", FieldType.SELECT, false, List.of(
                            opt("hours", "Ore"), opt("incidents", "Numero interventi"), opt("none", "Nessuno"))),
                    f("slaHours", "Ore SLA", FieldType.DECIMAL),
                    f("usedHours", "Ore consumate", FieldType.DECIMAL),
                    f("responseHours", "Tempo di risposta (h)", FieldType.DECIMAL),
                    f("value", "Valore", FieldType.CURRENCY)
            )),
            new ObjectSpec("asset", "Asset", "Asset", "cpu", "#475569", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    fRelation("company", "Cliente", Map.of("targetObject", "company", "multiple", false)),
                    fRelation("product", "Articolo", Map.of("targetObject", "product", "multiple", false)),
                    f("serial", "Numero di serie", FieldType.TEXT),
                    f("installedAt", "Installato il", FieldType.DATE),
                    f("warrantyUntil", "Garanzia fino a", FieldType.DATE),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("active", "Attivo"), opt("maintenance", "In manutenzione"), opt("dismissed", "Dismesso")))
            )),

            // progetti
            new ObjectSpec("project_task", "Compito di progetto", "Compiti di progetto", "list-checks", "#0D9488", List.of(
                    fReq("title", "Titolo", FieldType.TEXT),
                    fRelation("project", "Progetto", Map.of("targetObject", "project", "multiple", false)),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("todo", "Da fare"), opt("doing", "In corso"),
                            opt("blocked", "Bloccato"), opt("done", "Fatto"))),
                    f("assignee", "Assegnatario", FieldType.USER),
                    f("startDate", "Inizio", FieldType.DATE),
                    f("dueDate", "Scadenza", FieldType.DATE),
                    f("estimatedHours", "Ore stimate", FieldType.DECIMAL),
                    f("progress", "Avanzamento", FieldType.PERCENT)
            )),
            new ObjectSpec("project_template", "Template progettuale", "Templates progettuali", "layout-template", "#059669", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("description", "Descrizione", FieldType.LONGTEXT),
                    // elenco dei compiti da istanziare: [{title, offsetDays, estimatedHours}]
                    f("tasks", "Compiti", FieldType.JSON)
            )),
            new ObjectSpec("time_entry", "Time tracker", "Time tracker", "timer", "#CA8A04", List.of(
                    fReq("description", "Descrizione", FieldType.TEXT),
                    f("user", "Utente", FieldType.USER),
                    fReq("date", "Data", FieldType.DATE),
                    fReq("hours", "Ore", FieldType.DECIMAL),
                    fOpts("billable", "Fatturabile", FieldType.SELECT, false, List.of(
                            opt("yes", "Si'"), opt("no", "No"))),
                    f("hourlyRate", "Tariffa oraria", FieldType.CURRENCY),
                    fRelation("project", "Progetto", Map.of("targetObject", "project", "multiple", false)),
                    fRelation("ticket", "Ticket", Map.of("targetObject", "ticket", "multiple", false))
            )),

            // marketing
            new ObjectSpec("marketing_list", "Lista marketing", "Liste marketing", "users", "#DB2777", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("description", "Descrizione", FieldType.LONGTEXT),
                    fOpts("kind", "Tipo", FieldType.SELECT, false, List.of(
                            opt("static", "Statica"), opt("dynamic", "Dinamica"))),
                    // per le liste dinamiche: lo stesso JSON dei filtri del Filter Builder
                    f("filter", "Filtro", FieldType.JSON)
            )),
            new ObjectSpec("email_template", "Modello email", "Modelli email", "mail", "#BE185D", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("subject", "Oggetto", FieldType.TEXT),
                    f("body", "Corpo", FieldType.RICHTEXT),
                    f("objectKey", "Modulo di riferimento", FieldType.TEXT)
            )),
            new ObjectSpec("campaign", "Campagna", "Campagne", "megaphone", "#E11D48", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    fOpts("channel", "Canale", FieldType.SELECT, false, List.of(
                            opt("email", "Email"), opt("telemarketing", "Telemarketing"))),
                    fOpts("status", "Stato", FieldType.STATUS, false, List.of(
                            opt("draft", "Bozza"), opt("scheduled", "Programmata"),
                            opt("sending", "In invio"), opt("sent", "Inviata"), opt("cancelled", "Annullata"))),
                    fRelation("list", "Lista marketing", Map.of("targetObject", "marketing_list", "multiple", false)),
                    fRelation("template", "Modello Email", Map.of("targetObject", "email_template", "multiple", false)),
                    f("scheduledAt", "Programmata per", FieldType.DATETIME),
                    f("budget", "Budget", FieldType.CURRENCY),
                    // contatori aggiornati dal motore di invio (blocco campagne, non ora)
                    f("sentCount", "Inviate", FieldType.INTEGER),
                    f("openCount", "Aperture", FieldType.INTEGER),
                    f("clickCount", "Click", FieldType.INTEGER)
            )),
            new ObjectSpec("goal", "Obiettivo", "Obiettivi", "flag", "#9333EA", List.of(
                    fReq("name", "Nome", FieldType.TEXT),
                    f("owner", "Responsabile", FieldType.USER),
                    fOpts("metric", "Metrica", FieldType.SELECT, false, List.of(
                            opt("revenue", "Fatturato"), opt("opportunities", "Opportunita'"),
                            opt("leads", "Lead"), opt("custom", "Personalizzata"))),
                    f("target", "Obiettivo", FieldType.DECIMAL),
                    f("current", "Attuale", FieldType.DECIMAL),
                    f("periodStart", "Da", FieldType.DATE),
                    f("periodEnd", "A", FieldType.DATE)
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

            // la numerazione si accende solo se il tipo non ne ha già una: come per label e
            // icona, una configurazione fatta a mano dall'utente non va sovrascritta. La
            // condizione sul null serve anche ai tipi creati prima che il blocco 5 esistesse,
            // che altrimenti resterebbero senza numerazione per sempre
            if (spec.numbering() != null && obj.getNumberingField() == null) {
                NumberingSpec n = spec.numbering();
                obj.setNumberingField(n.field());
                obj.setNumberingPrefix(n.prefix());
                obj.setNumberingUseYear(n.useYear());
                obj.setNumberingPadding(n.padding());
                obj.setNumberingSeparator(n.separator());
                objectTypeRepository.save(obj);
                log.info("seed: numbering enabled on {} ({})", spec.key(), n.field());
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
