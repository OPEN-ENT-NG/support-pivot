package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.services.JiraService;
import fr.openent.supportpivot.services.MongoService;
import fr.openent.supportpivot.services.RouterService;
import fr.openent.supportpivot.services.impl.CrifRouterService;
import fr.openent.supportpivot.services.impl.JiraServiceImpl;
import fr.openent.supportpivot.services.impl.MongoServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class ServiceManager {
    protected static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static ServiceManager serviceManager = null;

    private final MongoService mongoService;
    private final RouterService routerService;
    private final JiraService jiraService;
    private final WebClient webClient;
    private final Vertx vertx;

    public static void init(Vertx vertx, JsonObject config) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config);
        }
    }

    private ServiceManager(Vertx vertx, JsonObject config) {
        this.mongoService = new MongoServiceImpl(ConfigManager.getInstance().getConfig().getMongoCollection());
        this.jiraService = new JiraServiceImpl();
        this.vertx = vertx;
        this.webClient = initWebClient();

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

    public WebClient getWebClient() { return webClient; }

    public Vertx getVertx() { return vertx; }

    public static ServiceManager getInstance(){ return serviceManager;}

    private WebClient initWebClient() {
        WebClientOptions options = new WebClientOptions();
        options.setSsl(true);
        options.setTrustAll(true);
        if (ConfigManager.getInstance().getConfig().getProxyHost() != null) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setHost(ConfigManager.getInstance().getConfig().getProxyHost());
            proxyOptions.setPort(ConfigManager.getInstance().getConfig().getProxyPort());
            proxyOptions.setUsername(ConfigManager.getInstance().getConfig().getProxyLogin());
            proxyOptions.setPassword(ConfigManager.getInstance().getConfig().getProxyPasswd());
            proxyOptions.setType(ProxyType.HTTP);
            options.setProxyOptions(proxyOptions);
        }
        return WebClient.create(this.vertx, options);
    }
}
