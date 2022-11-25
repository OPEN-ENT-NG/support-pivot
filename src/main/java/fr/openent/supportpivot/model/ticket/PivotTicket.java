package fr.openent.supportpivot.model.ticket;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PivotTicket {

    public final static String IDEXTERNAL_FIELD = "id_externe";
    @Deprecated
    public final static String IDIWS_FIELD = "id_iws";
    public final static String ID_FIELD = "id_ent";
    public final static String IDJIRA_FIELD = "id_jira";
    public final static String COLLECTIVITY_FIELD = "collectivite";
    public final static String ACADEMY_FIELD = "academie";
    public final static String CREATOR_FIELD = "demandeur";
    public final static String TICKETTYPE_FIELD = "type_demande";
    public final static String TITLE_FIELD = "titre";
    public final static String DESCRIPTION_FIELD = "description";
    public final static String PRIORITY_FIELD = "priorite";
    public final static String MODULES_FIELD = "modules";
    public final static String COMM_FIELD = "commentaires";
    public final static String ATTACHMENT_FIELD = "pj";
    public final static String ATTACHMENT_NAME_FIELD = "nom";
    public final static String ATTACHMENT_CONTENT_FIELD = "contenu";
    public final static String STATUSEXTERNAL_FIELD = "statut_externe";
    @Deprecated
    public final static String STATUSIWS_FIELD = "statut_iws";
    public final static String STATUSENT_FIELD = "statut_ent";
    public final static String STATUSJIRA_FIELD = "statut_jira";
    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String RAWDATE_CREA_FIELD = "creation";
    public final static String RAWDATE_UPDATE_FIELD = "maj";
    @Deprecated
    public final static String DATE_RESOIWS_FIELD = "date_resolution_iws";
    public final static String DATE_RESO_FIELD = "date_resolution_ent";
    public final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";
    public final static String TECHNICAL_RESP_FIELD= "reponse_technique";
    public final static String CLIENT_RESP_FIELD= "reponse_client";
    public final static String ATTRIBUTION_FIELD = "attribution";
    public final static String UAI_FIELD = "uai";

    private JsonObject jsonTicket = new JsonObject();

    public PivotTicket() {
        this.initComments();
        this.initPjs();
    }

    private void initComments() {
        if (this.getComments() == null) {
            this.jsonTicket.put(COMM_FIELD, new JsonArray());
        }
    }

    private void initPjs() {
        if (this.getPjs() == null) {
            this.jsonTicket.put(ATTACHMENT_FIELD, new JsonArray());
        }
    }

    public String getJiraId() {
        return jsonTicket.getString(IDJIRA_FIELD, null);
    }
    
    public String getExternalId() { return jsonTicket.getString(IDEXTERNAL_FIELD, jsonTicket.getString(IDIWS_FIELD, null)); }

    public String getId() {
        return jsonTicket.getString(ID_FIELD, null);
    }

    public JsonObject getJsonTicket() {
        if (this.jsonTicket == null) {
            this.jsonTicket = new JsonObject();
        }
        return this.jsonTicket;
    }


    public String getTitle() {
        return jsonTicket.getString(TITLE_FIELD);
    }

    public String getContent() {
        return jsonTicket.getString(DESCRIPTION_FIELD);
    }

    public JsonArray getComments() {
        return this.jsonTicket.getJsonArray(COMM_FIELD);
    }

    public JsonArray getPjs() { return this.jsonTicket.getJsonArray(ATTACHMENT_FIELD); }

    public String getRawCreatedAt() {
        return jsonTicket.getString(RAWDATE_CREA_FIELD);
    }

    public String getRawUpdatedAt() {
        return jsonTicket.getString(RAWDATE_UPDATE_FIELD);
    }

    public String getAttributed() {
        return jsonTicket.getString(ATTRIBUTION_FIELD);
    }


    public void setJiraId(String jiraId) {
        jsonTicket.put(IDJIRA_FIELD, jiraId.trim());
    }

    public PivotTicket setJsonObject(JsonObject ticket) {
        if (ticket != null) {
            this.jsonTicket = ticket;
            this.initComments();
            this.initPjs();
        }
        return this;
    }

    //Retrocompatibility use id_iws as field for external tool id
    public void setExternalId(String id) {
        jsonTicket.put(IDEXTERNAL_FIELD, id.trim());
    }
}
