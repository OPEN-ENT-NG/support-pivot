package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraComment implements IModel<JiraComment> {
    private String self;
    private String id;
    private JiraAuthor author;
    private String body;
    private JiraAuthor updateAuthor;
    private String created;
    private String updated;
    private JiraVisibility visibility;

    public JiraComment(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.id = jsonObject.getString(Field.ID, "");
        this.author = new JiraAuthor(jsonObject.getJsonObject(Field.AUTHOR, new JsonObject()));
        this.body = jsonObject.getString(Field.BODY, "");
        this.updateAuthor = new JiraAuthor(jsonObject.getJsonObject(Field.UPDATEAUTHOR, new JsonObject()));
        this.created = jsonObject.getString(Field.CREATED, "");
        this.updated = jsonObject.getString(Field.UPDATED, "");
        if (jsonObject.getJsonObject(Field.VISIBILITY) != null) {
            this.visibility = new JiraVisibility(jsonObject.getJsonObject(Field.VISIBILITY, new JsonObject()));
        }
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraComment setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraComment setId(String id) {
        this.id = id;
        return this;
    }

    public JiraAuthor getAuthor() {
        return author;
    }

    public JiraComment setAuthor(JiraAuthor author) {
        this.author = author;
        return this;
    }

    public String getBody() {
        return body;
    }

    public JiraComment setBody(String body) {
        this.body = body;
        return this;
    }

    public JiraAuthor getUpdateAuthor() {
        return updateAuthor;
    }

    public JiraComment setUpdateAuthor(JiraAuthor updateAuthor) {
        this.updateAuthor = updateAuthor;
        return this;
    }

    public String getCreated() {
        return created;
    }

    public JiraComment setCreated(String created) {
        this.created = created;
        return this;
    }

    public String getUpdated() {
        return updated;
    }

    public JiraComment setUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    public JiraVisibility getVisibility() {
        return visibility;
    }

    public JiraComment setVisibility(JiraVisibility visibility) {
        this.visibility = visibility;
        return this;
    }
}
