package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.ISearchTicket;
import io.vertx.core.json.JsonObject;

public class JiraSearch implements ISearchTicket, IModel<JiraSearch> {
    private String idJira;
    private String idExterne;
    private String idEnt;
    private String date;

    public JiraSearch() {
    }

    public JiraSearch(JsonObject jsonObject) {
        throw new RuntimeException("Unsupported operation");
    }

    public String getIdJira() {
        return idJira;
    }

    public JiraSearch setIdJira(String idJira) {
        this.idJira = idJira;
        return this;
    }

    public String getIdExterne() {
        return idExterne;
    }

    public JiraSearch setIdExterne(String idExterne) {
        this.idExterne = idExterne;
        return this;
    }

    public String getIdEnt() {
        return idEnt;
    }

    public JiraSearch setIdEnt(String idEnt) {
        this.idEnt = idEnt;
        return this;
    }

    public String getDate() {
        return date;
    }

    public JiraSearch setDate(String date) {
        this.date = date;
        return this;
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }
}
