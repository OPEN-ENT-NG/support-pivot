package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class JiraSearch implements IModel<JiraSearch> {
    private String expand;
    private int startAt;
    private int maxResults;
    private int total;
    private List<JiraTicket> issues;

    public JiraSearch(JsonObject jsonObject) {
        this.expand = jsonObject.getString(Field.EXPAND, "");
        this.startAt = jsonObject.getInteger(Field.STARTAT, 0);
        this.maxResults = jsonObject.getInteger(Field.MAXRESULTS, 0);
        this.total = jsonObject.getInteger(Field.TOTAL, 0);
        this.issues = IModelHelper.toList(jsonObject.getJsonArray(Field.ISSUES, new JsonArray()), JiraTicket.class);
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, false);
    }

    public String getExpand() {
        return expand;
    }

    public JiraSearch setExpand(String expand) {
        this.expand = expand;
        return this;
    }

    public int getStartAt() {
        return startAt;
    }

    public JiraSearch setStartAt(int startAt) {
        this.startAt = startAt;
        return this;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public JiraSearch setMaxResults(int maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public JiraSearch setTotal(int total) {
        this.total = total;
        return this;
    }

    public List<JiraTicket> getIssues() {
        return issues;
    }

    public JiraSearch setIssues(List<JiraTicket> issues) {
        this.issues = issues;
        return this;
    }
}
