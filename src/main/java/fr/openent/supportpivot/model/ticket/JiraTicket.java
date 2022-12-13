package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.managers.ConfigManager;
import io.vertx.core.json.JsonObject;

public class JiraTicket extends JsonObject {

    private JsonObject ticket = new JsonObject();

    public JsonObject getJiraTicket(){
        return ticket;
    }

    public JiraTicket(){
        ticket.put(Field.FIELDS, new JsonObject().put(Field.PROJECT, new JsonObject()
                .put(Field.KEY, ConfigManager.getInstance().getConfig().getJiraProjectKey())));
    }

    public JiraTicket(PivotTicket pivotTicket) {
        super();
        addFields(Field.SUMMARY, pivotTicket.getTitle());
        addFields(Field.DESCRIPTION, pivotTicket.getContent());
        addFields(Field.SUMMARY, pivotTicket.getTitle());
        addFields(Field.SUMMARY, pivotTicket.getTitle());
    }

    private void addFields(String key, Object value){
        if(value!= null && key != null){
            ticket.getJsonObject(Field.FIELDS).put(key, value);
        }
    }
}
