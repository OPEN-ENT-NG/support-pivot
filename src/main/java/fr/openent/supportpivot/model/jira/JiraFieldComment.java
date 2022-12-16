package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class JiraFieldComment implements IModel<JiraFieldComment> {
    List<JiraComment> comments;
    int maxResults;
    int total;
    int startAt;

    public JiraFieldComment(JsonObject jsonObject) {
        this.comments = IModelHelper.toList(jsonObject.getJsonArray(Field.COMMENTS, new JsonArray()), JiraComment.class);
        this.maxResults = jsonObject.getInteger(Field.MAXRESULTS, 0);
        this.total = jsonObject.getInteger(Field.TOTAL, 0);
        this.startAt = jsonObject.getInteger(Field.STARTAT, 0);
    }

    public JiraFieldComment() {
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public List<JiraComment> getComments() {
        return comments;
    }

    public JiraFieldComment setComments(List<JiraComment> comments) {
        this.comments = comments;
        return this;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public JiraFieldComment setMaxResults(int maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public JiraFieldComment setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getStartAt() {
        return startAt;
    }

    public JiraFieldComment setStartAt(int startAt) {
        this.startAt = startAt;
        return this;
    }
}
