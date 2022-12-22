package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.services.routers.RouterService;
import fr.wseduc.rs.Get;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

/**
 * Created by bertinettia on 12/08/2019.
 * <p>
 * Controller for glpi
 */

public class JiraController extends ControllerHelper {
    private RouterService routerService;

    protected static final Logger log = LoggerFactory.getLogger(JiraController.class);

    @Override
    public void init(Vertx vertx, final JsonObject config, RouteMatcher rm,
                     Map<String, SecuredAction> securedActions) {

        super.init(vertx, config, rm, securedActions);
        ServiceManager serviceManager = ServiceManager.getInstance();
        this.routerService = serviceManager.getRouteurService();

    }

    @Get("/updateTicket/:idjira")
//    @fr.wseduc.security.SecuredAction("glpi.test.trigger") //TODO (voir comment faire)
    public void updateTicket(final HttpServerRequest request) {
        final String idJira = request.params().get(Field.IDJIRA);
        routerService.toPivotTicket(Endpoint.ENDPOINT_JIRA, new JsonObject().put(JiraConstants.ID_JIRA, idJira))
                .onFailure(error -> Renders.renderJson(request, new JsonObject().put(Field.STATUS, Field.KO), 500))
                .onSuccess(event -> Renders.renderJson(request, new JsonObject().put(Field.STATUS, Field.OK), 200));
    }
}
