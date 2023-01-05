package fr.openent.supportpivot.model;

import fr.openent.supportpivot.constants.ConfigField;
import fr.openent.supportpivot.constants.EntConstants;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.model.status.config.EntStatusConfig;
import fr.openent.supportpivot.model.status.config.JiraStatusConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigModel implements IModel<ConfigModel> {
    private static final Logger log = LoggerFactory.getLogger(ConfigModel.class);

    private final String collectivity;
    private final String mongoCollection;
    private final String defaultTicketType;
    private final String defaultPriority;
    private final String jiraLogin;
    private final String jiraPasswd;
    private final String jiraHost;
    private final String jiraURL;
    private final String jiraBaseUri;
    private final String jiraProjectKey;
    private final List<String> jiraAllowedTicketType;
    private final List<String> jiraAllowedPriority;
    private final Map<String, String> jiraCustomFields;
    private final List<JiraStatusConfig> jiraStatusConfigMapping;
    private final JiraStatusConfig defaultJiraStatusConfig;
    private final List<EntStatusConfig> entStatusConfigMapping;
    private final EntStatusConfig defaultEntStatusConfig;
    private final String proxyHost;
    private final Integer proxyPort;
    private final String proxyLogin;
    private final String proxyPasswd;

    public ConfigModel(JsonObject config) {
        this.collectivity = config.getString(ConfigField.COLLECTIVITY, "");
        if(this.collectivity == null || collectivity.isEmpty()) {
            log.warn(String.format("[SupportPivot@%s::ConfigModel] Default collectivity absent from configuration", this.getClass().getSimpleName()));
        }
        //We remove "support.demandes" from config
        this.mongoCollection = config.getString(ConfigField.MONGO_DASH_COLLECTION);
        this.defaultTicketType = config.getString(ConfigField.DEFAULT_DASH_TICKETTYPE);
        this.defaultPriority = config.getString(ConfigField.DEFAULT_DASH_PRIORITY);
        this.jiraLogin = config.getString(ConfigField.JIRA_DASH_LOGIN);
        this.jiraPasswd = config.getString(ConfigField.JIRA_DASH_PASSWD);
        this.jiraHost = config.getString(ConfigField.JIRA_DASH_HOST).trim();
        this.jiraURL = config.getString(ConfigField.JIRA_DASH_URL);
        this.jiraBaseUri = config.getString(ConfigField.JIRA_DASH_BASE_DASH_URI);
        this.jiraProjectKey = config.getString(ConfigField.JIRA_DASH_PROJECT_DASH_KEY);
        this.jiraAllowedTicketType = config.getJsonArray(ConfigField.JIRA_DASH_ALLOWED_DASH_TICKETTYPE, new JsonArray()).stream()
                .filter(o -> o instanceof String)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.jiraAllowedPriority = config.getJsonArray(ConfigField.JIRA_DASH_ALLOWED_DASH_PRIORITY, new JsonArray()).stream()
                .filter(o -> o instanceof String)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.jiraCustomFields = config.getJsonObject(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS, new JsonObject()).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, stringObjectEntry -> stringObjectEntry.getValue().toString()));
        this.jiraStatusConfigMapping = config.getJsonObject(ConfigField.JIRA_DASH_STATUS_DASH_MAPPING, new JsonObject())
                .getJsonObject(Field.STATUTSJIRA, new JsonObject()).stream()
                .filter(jiraStatusEntry -> jiraStatusEntry.getValue() instanceof JsonArray)
                .map(jiraStatusEntry -> new JiraStatusConfig(jiraStatusEntry.getKey(), (JsonArray) jiraStatusEntry.getValue()))
                .collect(Collectors.toList());
        String defaultJiraStatusString = config.getJsonObject(ConfigField.JIRA_DASH_STATUS_DASH_MAPPING).getString(Field.STATUTSDEFAULT);
        this.defaultJiraStatusConfig = this.jiraStatusConfigMapping.stream()
                .filter(jiraStatusConfig -> jiraStatusConfig.getKey().equals(defaultJiraStatusString))
                .findFirst()
                .orElse(null);
        this.entStatusConfigMapping = config.getJsonObject(ConfigField.ENT_DASH_STATUS_DASH_MAPPING, new JsonObject())
                .getJsonObject(EntConstants.STATUTS_ENT, new JsonObject()).stream()
                .filter(entStatusEntry -> entStatusEntry.getValue() instanceof String)
                .map(entStatusEntry -> new EntStatusConfig(entStatusEntry.getKey(), (String) entStatusEntry.getValue()))
                .collect(Collectors.toList());
        String defaultEntStatusString = config.getJsonObject(ConfigField.ENT_DASH_STATUS_DASH_MAPPING, new JsonObject()).getString(EntConstants.STATUTSDEFAULTENT);
        this.defaultEntStatusConfig = this.entStatusConfigMapping.stream()
                .filter(entStatusConfig -> entStatusConfig.getName().equals(defaultEntStatusString))
                .findFirst()
                .orElse(null);
        this.proxyHost = config.getString(ConfigField.PROXY_DASH_HOST);
        this.proxyPort = config.getInteger(ConfigField.PROXY_DASH_PORT);
        this.proxyLogin = config.getString(ConfigField.PROXY_DASH_LOGIN);
        this.proxyPasswd = config.getString(ConfigField.PROXY_DASH_PASSWD);
    }

    @Override
    public JsonObject toJson() {
        JsonObject result = new JsonObject()
                .put(ConfigField.COLLECTIVITY, this.collectivity)
                .put(ConfigField.MONGO_DASH_COLLECTION, this.mongoCollection)
                .put(ConfigField.DEFAULT_DASH_TICKETTYPE, this.defaultTicketType)
                .put(ConfigField.DEFAULT_DASH_PRIORITY, this.defaultPriority)
                .put(ConfigField.JIRA_DASH_LOGIN, this.jiraLogin)
                .put(ConfigField.JIRA_DASH_PASSWD, this.jiraPasswd)
                .put(ConfigField.JIRA_DASH_HOST, this.jiraHost)
                .put(ConfigField.JIRA_DASH_URL, this.jiraURL)
                .put(ConfigField.JIRA_DASH_BASE_DASH_URI, this.jiraBaseUri)
                .put(ConfigField.JIRA_DASH_PROJECT_DASH_KEY, this.jiraProjectKey)
                .put(ConfigField.JIRA_DASH_ALLOWED_DASH_TICKETTYPE, new JsonArray(this.jiraAllowedTicketType).copy())
                .put(ConfigField.JIRA_DASH_ALLOWED_DASH_PRIORITY, new JsonArray(this.jiraAllowedPriority).copy());

        JsonObject jiraField = new JsonObject();
        this.jiraCustomFields.forEach(jiraField::put);

        JsonObject jiraStatus = new JsonObject();
        this.jiraStatusConfigMapping.forEach(jiraStatusConfig1 -> jiraStatus.put(jiraStatusConfig1.getKey(), new JsonArray(jiraStatusConfig1.getNameList()).copy()));
        JsonObject jiraMapping = new JsonObject()
                .put(Field.STATUTSJIRA, jiraStatus)
                .put(Field.STATUTSDEFAULT, this.defaultJiraStatusConfig.getKey());

        JsonObject entStatus = new JsonObject();
        this.entStatusConfigMapping.forEach(entStatusConfig1 -> entStatus.put(entStatusConfig1.getKey(), entStatusConfig1.getName()));
        JsonObject entMapping = new JsonObject()
                .put(Field.STATUTSENT, entStatus)
                .put(Field.STATUTSDEFAULTENT, this.defaultEntStatusConfig.getName());

        result.put(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS, jiraField)
                .put(ConfigField.JIRA_DASH_STATUS_DASH_MAPPING, jiraMapping)
                .put(ConfigField.ENT_DASH_STATUS_DASH_MAPPING, entMapping);

        return result;
    }

    public String getCollectivity() {
        return collectivity;
    }

    public String getMongoCollection() {
        return mongoCollection;
    }

    public String getDefaultTicketType() {
        return defaultTicketType;
    }

    public String getDefaultPriority() {
        return defaultPriority;
    }

    public String getJiraLogin() {
        return jiraLogin;
    }

    public String getJiraPasswd() {
        return jiraPasswd;
    }

    public String getJiraHost() {
        return jiraHost;
    }

    public String getJiraURL() {
        return jiraURL;
    }

    public String getJiraBaseUri() {
        return jiraBaseUri;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public List<String> getJiraAllowedTicketType() {
        return jiraAllowedTicketType;
    }

    public List<String> getJiraAllowedPriority() {
        return jiraAllowedPriority;
    }

    public Map<String, String> getJiraCustomFields() {
        return jiraCustomFields;
    }

    public List<JiraStatusConfig> getJiraStatusMapping() {
        return jiraStatusConfigMapping;
    }

    public JiraStatusConfig getDefaultJiraStatus() {
        return defaultJiraStatusConfig;
    }

    public List<EntStatusConfig> getEntStatusMapping() {
        return entStatusConfigMapping;
    }

    public EntStatusConfig getDefaultEntStatus() {
        return defaultEntStatusConfig;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public Integer getProxyPort() { return proxyPort; }

    public String getProxyLogin() { return proxyLogin; }

    public String getProxyPasswd() { return proxyPasswd; }
}
