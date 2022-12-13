package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraVisibility implements IModel<JiraVisibility> {
    private String type;
    private String value;

    public JiraVisibility(JsonObject jsonObject) {
        this.type = jsonObject.getString(Field.TYPE, "");
        this.value = jsonObject.getString(Field.VALUE, "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getType() {
        return type;
    }

    public JiraVisibility setType(String type) {
        this.type = type;
        return this;
    }

    public String getValue() {
        return value;
    }

    public JiraVisibility setValue(String value) {
        this.value = value;
        return this;
    }
}
