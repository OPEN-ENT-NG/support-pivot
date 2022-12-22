package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraFields implements IModel<JiraFields> {
    private Map<String, String> customFields = new HashMap<>();
    private String created;
    private String updated;
    private String summary;
    private String description;
    private JiraIssueType issuetype;
    private JiraPriority priority;
    private List<String> labels;
    private JiraFieldComment comment;
    private JiraStatus status;
    private List<JiraAttachment> attachment;
    private String resolutiondate;

    public JiraFields(JsonObject jsonObject) {
        jsonObject.copy().forEach(stringObjectEntry -> {
            if (stringObjectEntry.getKey().startsWith(Field.CUSTOMFIELD_)) {
                this.customFields.put(stringObjectEntry.getKey(), stringObjectEntry.getValue() == null ? null : stringObjectEntry.getValue().toString());
                jsonObject.getJsonObject(Field.FIELDS, new JsonObject()).remove(stringObjectEntry.getKey());
            }
        });
        this.created = jsonObject.getString(Field.CREATED);
        this.updated = jsonObject.getString(Field.UPDATED);
        this.summary = jsonObject.getString(Field.SUMMARY);
        this.description = jsonObject.getString(Field.DESCRIPTION);
        this.issuetype = new JiraIssueType(jsonObject.getJsonObject(Field.ISSUETYPE, new JsonObject()));
        this.priority = new JiraPriority(jsonObject.getJsonObject(Field.PRIORITY, new JsonObject()));
        this.labels = jsonObject.getJsonArray(Field.LABELS, new JsonArray()).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.comment = new JiraFieldComment(jsonObject.getJsonObject(Field.COMMENT, new JsonObject()));
        this.status = new JiraStatus(jsonObject.getJsonObject(Field.STATUS, new JsonObject()));
        this.attachment = IModelHelper.toList(jsonObject.getJsonArray(Field.ATTACHMENT, new JsonArray()), JiraAttachment.class);
        this.resolutiondate = jsonObject.getString(Field.RESOLUTIONDATE, "");
    }

    public JiraFields() {
    }

    @Override
    public JsonObject toJson() {
        JsonObject jsonObject = IModelHelper.toJson(this, true, false);
        customFields.forEach(jsonObject::put);
        return jsonObject;
    }

    public String getCustomFields(String key, String defaultValue) {
        return customFields.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public JiraFields setCustomFields(Map<String, String> customFields) {
        this.customFields = customFields;
        return this;
    }

    public String getCreated() {
        return created;
    }

    public JiraFields setCreated(String created) {
        this.created = created;
        return this;
    }

    public String getUpdated() {
        return updated;
    }

    public JiraFields setUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public JiraFields setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public JiraFields setDescription(String description) {
        this.description = description;
        return this;
    }

    public JiraIssueType getIssuetype() {
        return issuetype;
    }

    public JiraFields setIssuetype(JiraIssueType issuetype) {
        this.issuetype = issuetype;
        return this;
    }

    public JiraPriority getPriority() {
        return priority;
    }

    public JiraFields setPriority(JiraPriority priority) {
        this.priority = priority;
        return this;
    }

    public List<String> getLabels() {
        return labels;
    }

    public JiraFields setLabels(List<String> labels) {
        this.labels = labels;
        return this;
    }

    public JiraFieldComment getComment() {
        return comment;
    }

    public JiraFields setComment(JiraFieldComment comment) {
        this.comment = comment;
        return this;
    }

    public JiraStatus getStatus() {
        return status;
    }

    public JiraFields setStatus(JiraStatus status) {
        this.status = status;
        return this;
    }

    public List<JiraAttachment> getAttachment() {
        return attachment;
    }

    public JiraFields setAttachment(List<JiraAttachment> attachment) {
        this.attachment = attachment;
        return this;
    }

    public String getResolutiondate() {
        return resolutiondate;
    }

    public JiraFields setResolutiondate(String resolutiondate) {
        this.resolutiondate = resolutiondate;
        return this;
    }
}
