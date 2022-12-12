package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraAuthor implements IModel<JiraAuthor> {
    private String self;
    private String name;
    private String key;
    private String emailAddress;
    //Todo if we need avatar url use this
    //private AvatarUrls avatarUrls;
    private String displayName;
    private boolean active;
    private String timeZone;

    public JiraAuthor(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.name = jsonObject.getString(Field.NAME, "");
        this.key = jsonObject.getString(Field.KEY, "");
        this.emailAddress = jsonObject.getString(Field.EMAILADDRESS, "");
        this.displayName = jsonObject.getString(Field.DISPLAYNAME, "");
        this.active = jsonObject.getBoolean(Field.ACTIVE, false);
        this.timeZone = jsonObject.getString(Field.TIMEZONE, "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraAuthor setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getName() {
        return name;
    }

    public JiraAuthor setName(String name) {
        this.name = name;
        return this;
    }

    public String getKey() {
        return key;
    }

    public JiraAuthor setKey(String key) {
        this.key = key;
        return this;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public JiraAuthor setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public JiraAuthor setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    public JiraAuthor setActive(boolean active) {
        this.active = active;
        return this;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public JiraAuthor setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }
}
