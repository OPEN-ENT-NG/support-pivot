package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraStatusCategory implements IModel<JiraStatusCategory> {
    private String self;
    private int id;
    private String key;
    private String colorName;
    private String name;

    public JiraStatusCategory(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.id = jsonObject.getInteger(Field.ID, 0);
        this.key = jsonObject.getString(Field.KEY, "");
        this.colorName = jsonObject.getString(Field.COLORNAME, "");
        this.name = jsonObject.getString(Field.NAME, "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraStatusCategory setSelf(String self) {
        this.self = self;
        return this;
    }

    public int getId() {
        return id;
    }

    public JiraStatusCategory setId(int id) {
        this.id = id;
        return this;
    }

    public String getKey() {
        return key;
    }

    public JiraStatusCategory setKey(String key) {
        this.key = key;
        return this;
    }

    public String getColorName() {
        return colorName;
    }

    public JiraStatusCategory setColorName(String colorName) {
        this.colorName = colorName;
        return this;
    }

    public String getName() {
        return name;
    }

    public JiraStatusCategory setName(String name) {
        this.name = name;
        return this;
    }
}
