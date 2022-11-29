package fr.openent.supportpivot.model.endpoint.jira;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class JiraFilterBuilder extends JsonObject {

    private static final Logger log = LoggerFactory.getLogger(JiraFilterBuilder.class);
    StringBuilder jqlQueryString = new StringBuilder();
    String outputFields = "";

    public void addAssigneeFilter(String assignee) {
        if (assignee != null) jqlQueryString.append(addFilter(("assignee = " + assignee)));
    }

    public void addAssigneeOrCustomFieldFilter(String assignee, String customFielName, String customFieldValue) {

        StringBuilder tmpFilter = new StringBuilder();
        if (assignee != null) {
            tmpFilter.append("assignee = ").append(assignee);

        }
        if(customFielName != null && !customFielName.isEmpty()) {
            String customFielId = customFielName.replaceAll("customfield_", "");
            if(!tmpFilter.toString().isEmpty()) {
                tmpFilter.append(" or ");
            }
            tmpFilter.append(getCustomFieldTemplate(customFielId, customFieldValue));
        }
        if(!tmpFilter.toString().isEmpty()) {
            tmpFilter.insert(0, '(');
            tmpFilter.append(')');
            jqlQueryString.append(tmpFilter);
        }
    }

    private String getCustomFieldTemplate(String id, String value) {
        String cf = "cf[" + id + "]";
        if(value != null) {
            return cf + " ~ " + value;
        } else {
            return cf + " is not EMPTY";
        }
    }

    public void addMinUpdateDate(String date) {
        if(date != null && !date.isEmpty()) {
            // format date
            jqlQueryString.append(addFilter("updated > '" + date + "'"));
        }
    }

    public void addCustomfieldFilter(String customfieldid, String value) {
        if (customfieldid != null && value != null)
            jqlQueryString.append(addFilter(getCustomFieldTemplate(customfieldid, value)));
    }

    public void onlyIds() {
        outputFields = "&fields=id,key";
    }

    public void addFieldDates() {
        addFields(",updated,created");
    }

    public void addFields(String fields) {
        if(outputFields.isEmpty()) {
            outputFields = "&fields=" + fields;
        } else {
            outputFields += "," + fields;
        }
    }

    public String buildSearchQueryString() {
        try {
            return "jql=" + URLEncoder.encode(jqlQueryString.toString(), StandardCharsets.UTF_8.toString()) + outputFields;
        } catch (UnsupportedEncodingException e) {
            log.error("Error during build Jira search request :" + jqlQueryString.toString());
            return "";
        }
    }

    private String addFilter(String filter) {

        if (jqlQueryString.length() == 0) {
            return filter;
        } else {
            return " AND " + filter;
        }
    }

}
