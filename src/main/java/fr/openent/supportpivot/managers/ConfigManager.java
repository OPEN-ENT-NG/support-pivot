package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.EntConstants;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class ConfigManager {

    private static ConfigManager instance = null;

    private static JsonObject rawConfig;
    private final JsonObject publicConfig;

    private final String collectivity;
    private final String mongoCollection;
    private final String proxyHost;
    private final Integer proxyPort;

    private final String jiraHost;
    private final String jiraBaseUri;
    private final String jiraProjectKey;
    private final String jiraLogin;
    private final String jiraPassword;

    private final JsonObject jiraCustomFields;
    private final String jiraCustomFieldIdForExternalId;
    private final String jiraCustomFieldIdForIdent;
    private final String jiraDefaultStatus;
    private final JsonObject jiraStatusMapping;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);


    private ConfigManager() {

        publicConfig = rawConfig.copy();
        collectivity = rawConfig.getString("collectivity");
        if(collectivity.isEmpty()) {
            log.warn("Default collectivity absent from configuration");
        }
        mongoCollection = rawConfig.getString("mongo-collection", "support.demandes");
        proxyHost = rawConfig.getString("proxy-host", null);
        proxyPort = rawConfig.getInteger("proxy-port");

        //JIRA configuration
        jiraHost = rawConfig.getString("jira-host").trim() ;
        CheckUrlSyntax(jiraHost, "jira-host");
        jiraBaseUri = rawConfig.getString("jira-base-uri");
        jiraProjectKey = rawConfig.getString("jira-project-key");
        jiraLogin = rawConfig.getString("jira-login");
        jiraPassword = rawConfig.getString("jira-passwd");
        jiraCustomFields = rawConfig.getJsonObject("jira-custom-fields");
        jiraCustomFieldIdForIdent = jiraCustomFields.getString("id_ent");
        if(jiraCustomFields.containsKey("id_externe")) {
            jiraCustomFieldIdForExternalId = jiraCustomFields.getString("id_externe");
        }else{
            //For retro-compatibility
            jiraCustomFieldIdForExternalId = jiraCustomFields.getString(EntConstants.IDENT_FIELD);
        }
        jiraStatusMapping = rawConfig.getJsonObject("jira-status-mapping").getJsonObject("statutsJira");
        jiraDefaultStatus = rawConfig.getJsonObject("jira-status-mapping").getString("statutsDefault");
        initPublicConfig();
        log.info("SUPPORT-PIVOT CONFIGURATION : " + publicConfig.toString());
    }

    private void initPublicConfig() {
        publicConfig.put("jira-passwd", hidePasswd(rawConfig.getString("jira-passwd", "")));
    }

    private static void CheckUrlSyntax(String URLvalue, String parameterName) {
        try {
            new URL(URLvalue).toURI();
        }catch (Exception e) {
            log.error("entcore.json : parameter " + parameterName+" is not a valid URL",e);
        }
    }

    private String hidePasswd(String passwd) {
        String newpwd;
        if(passwd.length() < 8) {
            newpwd = StringUtils.repeat("*", passwd.length());
        } else {
            newpwd = passwd.substring(0, 3) + StringUtils.repeat("*", passwd.length() - 3);
        }
        return newpwd;
    }

    public JsonObject getPublicConfig() { return publicConfig; }

    public String getCollectivity() { return collectivity; }
    public String getMongoCollection() { return mongoCollection; }
    public String getProxyHost() { return proxyHost; }
    public Integer getProxyPort() { return proxyPort; }

    public String getJiraHost() { return jiraHost; }
    public URI getJiraBaseUrl() {
        try {
            return new URI(jiraHost).resolve(jiraBaseUri);
        } catch (URISyntaxException e) {
            log.fatal("bad URL jira-host / jira-base-uri :" +  jiraHost + " / " + jiraBaseUri);
            return URI.create("");
        }
    }
    public String getJiraLogin() { return jiraLogin; }
    public String getJiraPassword() { return jiraPassword; }
    public String getJiraAuthInfo() { return jiraLogin + ":" + jiraPassword; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public JsonObject getJiraCustomFields() { return jiraCustomFields; }
    public String getJiraCustomFieldIdForExternalId() { return jiraCustomFieldIdForExternalId; }
    public String getjiraCustomFieldIdForIdent() { return jiraCustomFieldIdForIdent; }
    public JsonObject getJiraStatusMapping() { return jiraStatusMapping; }
    public String getJiraDefaultStatus() { return jiraDefaultStatus; }
    public static void init(JsonObject configuration) {
        rawConfig = configuration;
        instance = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return instance;
    }


}
