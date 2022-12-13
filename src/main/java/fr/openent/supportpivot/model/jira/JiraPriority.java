package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraPriority implements IModel<JiraPriority> {
    private String self;
    private String iconUrl;
    private String name;
    private String id;

    public JiraPriority(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.iconUrl = jsonObject.getString(Field.ICONURL, "");
        this.name = jsonObject.getString(Field.NAME, "");
        this.id = jsonObject.getString(Field.ID, "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraPriority setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public JiraPriority setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    public String getName() {
        return name;
    }

    public JiraPriority setName(String name) {
        this.name = name;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraPriority setId(String id) {
        this.id = id;
        return this;
    }
}
