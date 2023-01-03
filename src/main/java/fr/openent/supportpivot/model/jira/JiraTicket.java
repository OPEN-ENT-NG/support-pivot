package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.json.JsonObject;

public class JiraTicket implements IModel<JiraTicket>, IPivotTicket {
    private String expand;
    private String id;
    private String self;
    private String key;
    private JiraFields fields;

    public JiraTicket(JsonObject jsonObject) {
        this.expand = jsonObject.getString(Field.EXPAND, "");
        this.id = jsonObject.getString(Field.ID, "");
        this.self = jsonObject.getString(Field.SELF, "");
        this.key = jsonObject.getString(Field.KEY, "");
        this.fields = new JiraFields(jsonObject.getJsonObject(Field.FIELDS, new JsonObject()));
    }

    public JiraTicket() {
    }

    public JiraTicket(PivotTicket pivotTicket) {
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getExpand() {
        return expand;
    }

    public JiraTicket setExpand(String expand) {
        this.expand = expand;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraTicket setId(String id) {
        this.id = id;
        return this;
    }

    public String getSelf() {
        return self;
    }

    public JiraTicket setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getKey() {
        return key;
    }

    public JiraTicket setKey(String key) {
        this.key = key;
        return this;
    }

    public JiraFields getFields() {
        return fields;
    }

    public JiraTicket setFields(JiraFields fields) {
        this.fields = fields;
        return this;
    }
}
