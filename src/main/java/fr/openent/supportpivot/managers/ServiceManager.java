package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.services.*;
import fr.openent.supportpivot.services.routers.CrifRouterService;
import fr.openent.supportpivot.services.routers.RouterService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.email.EmailFactory;

public class ServiceManager {
    protected static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static ServiceManager serviceManager = null;

    private MongoService mongoService;
    private RouterService routeurService;

    public static void init(Vertx vertx, JsonObject config) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config);
        }
    }

    private ServiceManager(Vertx vertx, JsonObject config) {

        EmailFactory emailFactory = new EmailFactory(vertx, config);
        EmailSender emailSender = emailFactory.getSender();

        mongoService = new MongoService(ConfigManager.getInstance().getConfig().getMongoCollection());
        HttpClientService httpClientService = new HttpClientService(vertx);

        switch (ConfigManager.getInstance().getConfig().getCollectivity()) {
            case "CRIF":
                log.info("Start Pivot with CRIF Routeur.");
                JiraService jiraService = new JiraServiceImpl(vertx);
                routeurService = new CrifRouterService(httpClientService, jiraService);
                break;
            default:
                log.error(String.format("[SupportPivot@%s::ServiceManager] Unknown value when starting Pivot Service. collectivity: %s",
                        this.getClass().getName(), ConfigManager.getInstance().getConfig().getCollectivity()));
        }
    }

    public MongoService getMongoService() { return mongoService; }

    public RouterService getRouteurService() { return routeurService; }

    public static ServiceManager getInstance(){ return serviceManager;}
}
