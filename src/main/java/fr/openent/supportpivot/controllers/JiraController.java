package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.jira.JiraSearch;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.rs.Get;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;

/**
 * Created by bertinettia on 12/08/2019.
 * <p>
 * Controller for glpi
 */

public class JiraController extends ControllerHelper {
    private final RouterService routerService;

    protected static final Logger log = LoggerFactory.getLogger(JiraController.class);

    public JiraController() {
        this.routerService = ServiceManager.getInstance().getRouterService();
    }


    @Get("/updateJira/:idjira")
//    @fr.wseduc.security.SecuredAction("glpi.test.trigger") //TODO (voir comment faire)
    //todo rajouter la save du du ticket en mongo
    public void updateTicketJira(final HttpServerRequest request) {
        final String idJira = request.params().get(Field.IDJIRA);
        JiraSearch jiraSearch = new JiraSearch().setIdJira(idJira);
        JsonObject jsonObject = new JsonObject();
        routerService.getPivotTicket(EndpointFactory.getJiraEndpoint(), jiraSearch)
                .compose(pivotTicket -> routerService.setPivotTicket(EndpointFactory.getPivotEndpoint(), pivotTicket))
                .compose(supportTicket -> {
                    jsonObject.put(Field.RESULT, supportTicket.toJson());
                    return ServiceManager.getInstance().getMongoService().saveTicket("updateTicketJira", supportTicket.toJson());
                })
                .onSuccess(event -> Renders.renderJson(request, jsonObject.getJsonObject(Field.RESULT)))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::process] Fail update ticket by Jira %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    Renders.renderError(request);
                });
    }
}
