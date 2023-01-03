package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import io.vertx.core.Vertx;

public class EndpointFactory {
    private static EndpointFactory endpointFactory = null;

    private final JiraEndpoint jiraEndpoint;
    private final SupportEndpoint supportEndpoint;
    private final LdeEndPoint ldeEndPoint;

    private EndpointFactory(Vertx vertx) {
        jiraEndpoint = new JiraEndpoint();
        supportEndpoint = new SupportEndpoint(vertx);
        ldeEndPoint = new LdeEndPoint();
    }

    public static JiraEndpoint getJiraEndpoint() {
        return endpointFactory.jiraEndpoint;
    }

    public static SupportEndpoint getPivotEndpoint() {
        return endpointFactory.supportEndpoint;
    }

    public static LdeEndPoint getLdeEndpoint() {
        return endpointFactory.ldeEndPoint;
    }

    public static void init(Vertx vertx) {
        if(endpointFactory == null) {
            endpointFactory = new EndpointFactory(vertx);
        }
    }
}
