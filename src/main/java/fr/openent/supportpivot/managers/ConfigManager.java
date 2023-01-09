package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.ConfigField;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.model.ConfigModel;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {

    private static ConfigManager instance = null;

    private static JsonObject rawConfig;
    private final ConfigModel config;
    private final JsonObject publicConfig;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);


    private ConfigManager() {
        config = new ConfigModel(rawConfig);
        checkUrlSyntax();
        checkMissingFields();
        publicConfig = rawConfig.copy();
        initPublicConfig();
        log.info("SUPPORT-PIVOT CONFIGURATION : " + publicConfig.toString());
    }

    /**
     * Send log when one jira custom field is missing
     */
    private void checkMissingFields() {
        List<String> requiredFieldsList = Arrays.asList(Field.ID_ENT, Field.ID_EXTERNE, Field.STATUS_ENT,
                Field.STATUS_EXTERNE, Field.CREATION, Field.RESOLUTION_ENT, Field.CREATOR,
                Field.RESPONSE_TECHNICAL, Field.UAI);
        requiredFieldsList.stream()
                .filter(field -> !config.getJiraCustomFields().containsKey(field))
                .forEach(field -> log.error(String.format("[SupportPivot@%s::checkMissingFields] Missing jira custom field %s", this.getClass().getSimpleName(), field)));
    }

    public URI getJiraBaseUrl() {
        try {
            return new URI(config.getJiraHost()).resolve(config.getJiraBaseUri());
        } catch (URISyntaxException e) {
            log.error(String.format("[SupportPivot@%s::getJiraBaseUrl] Bad URL jira-host / jira-base-uri :  %s / %s", this.getClass().getSimpleName(), config.getJiraHost(), config.getJiraBaseUri()));
            return URI.create("");
        }
    }

    public String getJiraLogin() { return config.getJiraLogin();}

    public String getJiraPassword() { return config.getJiraPasswd();}

    public String getJiraCustomFieldIdForExternalId() { return config.getJiraCustomFields().getOrDefault(Field.ID_EXTERNE, ""); }

    public String getJiraCustomFieldIdForIdent() { return config.getJiraCustomFields().getOrDefault(Field.ID_ENT, ""); }

    private void initPublicConfig() {
        publicConfig.put(ConfigField.JIRA_DASH_PASSWD, hidePasswd(rawConfig.getString(ConfigField.JIRA_DASH_PASSWD, "")));
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

    private void checkUrlSyntax() {
        try {
            new URL(config.getJiraHost()).toURI();
        }catch (Exception e) {
            log.error(String.format("[SupportPivot@%s::checkMissingFields] entcore.json : parameter %s is not a valid URL %s", this.getClass().getSimpleName(), ConfigField.JIRA_DASH_HOST, e));
        }
    }

    public static void init(JsonObject configuration) {
        rawConfig = configuration;
        instance = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return instance;
    }

    public ConfigModel getConfig() {
        return config;
    }

    public JsonObject getPublicConfig() {
        return publicConfig;
    }
}
