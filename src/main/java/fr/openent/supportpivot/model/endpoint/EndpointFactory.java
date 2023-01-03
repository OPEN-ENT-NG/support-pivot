package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import io.vertx.core.Vertx;

public class EndpointFactory {
    private static JiraEndpoint jiraEndpoint;
    private static SupportEndpoint supportEndpoint;
    private static LdeEndPoint ldeEndPoint;

    public static JiraEndpoint getJiraEndpoint() {
        return jiraEndpoint;
    }

    public static SupportEndpoint getPivotEndpoint() {
        return supportEndpoint;
    }

    public static LdeEndPoint getLdeEndpoint() {
        return ldeEndPoint;
    }

    public static void init(Vertx vertx, HttpClientService httpClientService, JiraService jiraService) {
        jiraEndpoint = new JiraEndpoint(httpClientService, jiraService);
        supportEndpoint = new SupportEndpoint(vertx);
        ldeEndPoint = new LdeEndPoint();
    }
}
