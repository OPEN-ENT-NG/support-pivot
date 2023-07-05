package fr.openent.supportpivot.model;

import fr.openent.supportpivot.constants.ConfigField;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class ConfigModelTest {

    @Test
    public void configModelTest(TestContext ctx) {
        ConfigModel conf = new ConfigModel(getConfig1());
        JsonObject confJson = conf.toJson();

        JsonObject confexpected = getConfig1();

        ctx.assertEquals(confexpected.getJsonObject(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS).size(), confJson.getJsonObject(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS).size());
        confJson.getJsonObject(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS).forEach(stringObjectEntry ->
                ctx.assertEquals(confJson.getJsonObject(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS).getString(stringObjectEntry.getKey()), stringObjectEntry.getValue()));

        ctx.assertEquals(confexpected.getJsonObject(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING).size(), confJson.getJsonObject(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING).size());
        confJson.getJsonObject(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING).forEach(stringObjectEntry ->
                ctx.assertEquals(confJson.getJsonObject(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING).getString(stringObjectEntry.getKey()), stringObjectEntry.getValue()));

        confJson.getJsonObject(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING, new JsonObject()).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, stringObjectEntry -> stringObjectEntry.getValue().toString()));
        confJson.remove(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS);
        confexpected.remove(ConfigField.JIRA_DASH_CUSTOM_DASH_FIELDS);

        confJson.remove(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING);
        confexpected.remove(ConfigField.ENT_DASH_JIRA_DASH_CATEGORY_DASH_MAPPING);

        ctx.assertEquals(confJson.toString(), confexpected.toString());
    }

    public static JsonObject getConfig1() {
        return new JsonObject("{\n" +
                "  \"collectivity\": \"CRIF\",\n" +
                "  \"mongo-collection\": \"pivot\",\n" +
                "  \"default-tickettype\": \"Assistance\",\n" +
                "  \"default-priority\": \"Mineur\",\n" +
                "  \"jira-login\": \"assistanceMLN\",\n" +
                "  \"jira-passwd\": \"mySuperStrongPassword\",\n" +
                "  \"jira-host\": \"https://jira-test.support-ent.fr\",\n" +
                "  \"jira-url\": \"/rest/api/2/issue/\",\n" +
                "  \"jira-base-uri\": \"/rest/api/2/\",\n" +
                "  \"jira-project-key\": \"FICTEST\",\n" +
                "  \"jira-allowed-tickettype\": [\n" +
                "    \"Assistance\",\n" +
                "    \"Incident\",\n" +
                "    \"Anomalie\",\n" +
                "    \"Service\"\n" +
                "  ],\n" +
                "  \"jira-allowed-priority\": [\n" +
                "    \"Lowest\",\n" +
                "    \"High\",\n" +
                "    \"Highest\"\n" +
                "  ],\n" +
                "  \"jira-custom-fields\": {\n" +
                "    \"id_ent\": \"customfield_12708\",\n" +
                "    \"id_externe\": \"customfield_13401\",\n" +
                "    \"status_ent\": \"customfield_12711\",\n" +
                "    \"status_externe\": \"customfield_13402\",\n" +
                "    \"creation\": \"customfield_12705\",\n" +
                "    \"resolution_ent\": \"customfield_12706\",\n" +
                "    \"creator\": \"customfield_11405\",\n" +
                "    \"response_technical\": \"customfield_12710\",\n" +
                "    \"uai\": \"customfield_13403\"\n" +
                "  },\n" +
                "  \"jira-status-mapping\": {\n" +
                "    \"statutsJira\": {\n" +
                "      \"Nouveau\": [\n" +
                "        \"Nouveau\"\n" +
                "      ],\n" +
                "      \"Ouvert\": [\n" +
                "        \"En cours\",\n" +
                "        \"En attente\"\n" +
                "      ],\n" +
                "      \"Résolu\": [\n" +
                "        \"Résolu sans livraison\",\n" +
                "        \"Resolu sans livraison\",\n" +
                "        \"Résolu et à tester\",\n" +
                "        \"Resolu et a tester\"\n" +
                "      ],\n" +
                "      \"Fermé\": [\n" +
                "        \"Fermé\",\n" +
                "        \"Ferme\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"statutsDefault\": \"Ouvert\"\n" +
                "  },\n" +
                "  \"ent-status-mapping\": {\n" +
                "    \"statutsEnt\": {\n" +
                "      \"1\": \"Nouveau\",\n" +
                "      \"2\": \"Ouvert\",\n" +
                "      \"3\": \"Resolu\",\n" +
                "      \"4\": \"Ferme\"\n" +
                "    },\n" +
                "    \"statutsDefaultEnt\": \"Ouvert\"\n" +
                "  },\n" +
                "\"ent-jira-category-mapping\": {" +
                "\"/pages\" : \"Pages\" }" +
                "}");
    }
}
