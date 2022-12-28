package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.services.*;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.impl.CrifRouterService;
import fr.openent.supportpivot.services.impl.HttpClientServiceImpl;
import fr.openent.supportpivot.services.impl.JiraServiceImpl;
import fr.openent.supportpivot.services.impl.MongoServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ServiceManager {
    protected static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static ServiceManager serviceManager = null;

    private final MongoService mongoService;
    private final RouterService routerService;
    private final JiraService jiraService;
    private final HttpClientService httpClientService;

    public static void init(Vertx vertx, JsonObject config) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config);
        }
    }

    private ServiceManager(Vertx vertx, JsonObject config) {
        this.mongoService = new MongoServiceImpl(ConfigManager.getInstance().getConfig().getMongoCollection());
        this.httpClientService = new HttpClientServiceImpl(vertx);
        this.jiraService = new JiraServiceImpl(vertx);

        switch (ConfigManager.getInstance().getConfig().getCollectivity()) {
            case "CRIF":
                log.info(String.format("[SupportPivot@%s::ServiceManager] Start Pivot with CRIF Routeur.",
                        this.getClass().getSimpleName()));
                routerService = new CrifRouterService();
                break;
            default:
                routerService = null;
                log.error(String.format("[SupportPivot@%s::ServiceManager] Unknown value when starting Pivot Service. collectivity: %s",
                        this.getClass().getSimpleName(), ConfigManager.getInstance().getConfig().getCollectivity()));
        }
    }

    public MongoService getMongoService() { return mongoService; }

    public RouterService getRouterService() { return routerService; }

    public JiraService getJiraService() { return jiraService; }

    public HttpClientService getHttpClientService() { return httpClientService; }

    public static ServiceManager getInstance(){ return serviceManager;}
}
