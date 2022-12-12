package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class JiraAttachment implements IModel<JiraAttachment> {
    private String self;
    private String id;
    private String filename;
    private JiraAuthor author;
    private String created;
    private int size;
    private String mimeType;
    private String content;
    private String thumbnail;

    public JiraAttachment() {
    }

    public JiraAttachment(JsonObject jsonObject) {
        this.self = jsonObject.getString(Field.SELF, "");
        this.id = jsonObject.getString(Field.ID, "");
        this.filename = jsonObject.getString(Field.FILENAME, "");
        this.author = new JiraAuthor(jsonObject.getJsonObject(Field.AUTHOR, new JsonObject()));
        this.created = jsonObject.getString(Field.CREATED, "");
        this.size = jsonObject.getInteger(Field.SIZE, 0);
        this.mimeType = jsonObject.getString(Field.MIMETYPE, "");
        this.content = jsonObject.getString(Field.CONTENT, "");
        this.thumbnail = jsonObject.getString(Field.THUMBNAIL, "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getSelf() {
        return self;
    }

    public JiraAttachment setSelf(String self) {
        this.self = self;
        return this;
    }

    public String getId() {
        return id;
    }

    public JiraAttachment setId(String id) {
        this.id = id;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public JiraAttachment setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public JiraAuthor getAuthor() {
        return author;
    }

    public JiraAttachment setAuthor(JiraAuthor author) {
        this.author = author;
        return this;
    }

    public String getCreated() {
        return created;
    }

    public JiraAttachment setCreated(String created) {
        this.created = created;
        return this;
    }

    public int getSize() {
        return size;
    }

    public JiraAttachment setSize(int size) {
        this.size = size;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public JiraAttachment setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public String getContent() {
        return content;
    }

    public JiraAttachment setContent(String content) {
        this.content = content;
        return this;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public JiraAttachment setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }
}
