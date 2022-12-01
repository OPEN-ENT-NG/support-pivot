package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.services.*;
import fr.openent.supportpivot.services.routers.CrifRouterService;
import fr.openent.supportpivot.services.routers.MdpRouterService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.email.EmailFactory;

public class ServiceManager {
    protected static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static ServiceManager serviceManager = null;

    private DefaultDemandeServiceImpl demandeService;
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
       demandeService = new DefaultDemandeServiceImpl(vertx, config, emailSender, mongoService);
        HttpClientService httpClientService = new HttpClientService(vertx);

        switch( ConfigManager.getInstance().getConfig().getCollectivity() ) {
            case "MDP":
                log.info("Start Pivot with MDP Routeur.");
                //TODO MDP Manager
                routeurService = new MdpRouterService();
                break;
            case "CRIF":
                log.info("Start Pivot with CRIF Routeur.");
                JiraService jiraService = new JiraServiceImpl(vertx);
                routeurService = new CrifRouterService(httpClientService,jiraService);
                break;
            default:
                log.error("Unknown value when starting Pivot Service. collectivity: " + ConfigManager.getInstance().getConfig().getCollectivity());
        } 
    }

    public DemandeService getDemandeService() { return demandeService; }
    public MongoService getMongoService() { return mongoService; }
    public RouterService getRouteurService() { return routeurService; }

    public static ServiceManager getInstance(){ return serviceManager;}



}
