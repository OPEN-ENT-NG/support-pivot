package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraStatus implements IModel<JiraStatus> {
    private String self;
    private String description;
    private String iconUrl;
    private String name;
    private String id;
    private JiraStatusCategory statusCategory;

    public JiraStatus(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.description = jsonObject.getString(Field.DESCRIPTION, "");
        this.iconUrl = jsonObject.getString(Field.ICONURL, "");
        this.name = jsonObject.getString(Field.NAME, "");
        this.id = jsonObject.getString(Field.ID, "");
        this.statusCategory = new JiraStatusCategory(jsonObject.getJsonObject(Field.STATUSCATEGORY, new JsonObject()));
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraStatus setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public JiraStatus setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public JiraStatus setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    public String getName() {
        return name;
    }

    public JiraStatus setName(String name) {
        this.name = name;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraStatus setId(String id) {
        this.id = id;
        return this;
    }

    public JiraStatusCategory getStatusCategory() {
        return statusCategory;
    }

    public JiraStatus setStatusCategory(JiraStatusCategory statusCategory) {
        this.statusCategory = statusCategory;
        return this;
    }
}
