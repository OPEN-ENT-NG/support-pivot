package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraIssueType implements IModel<JiraIssueType> {
    private String self;
    private String id;
    private String description;
    private String iconUrl;
    private String name;
    private boolean subtask;
    private int avatarId;

    public JiraIssueType(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.id = jsonObject.getString(Field.ID, "");
        this.description = jsonObject.getString(Field.DESCRIPTION, "");
        this.iconUrl = jsonObject.getString(Field.ICONURL, "");
        this.name = jsonObject.getString(Field.NAME, "");
        this.subtask = jsonObject.getBoolean(Field.SUBTASK, false);
        this.avatarId = jsonObject.getInteger(Field.AVATARID, 0);
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraIssueType setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraIssueType setId(String id) {
        this.id = id;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public JiraIssueType setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public JiraIssueType setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    public String getName() {
        return name;
    }

    public JiraIssueType setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isSubtask() {
        return subtask;
    }

    public JiraIssueType setSubtask(boolean subtask) {
        this.subtask = subtask;
        return this;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public JiraIssueType setAvatarId(int avatarId) {
        this.avatarId = avatarId;
        return this;
    }
}
