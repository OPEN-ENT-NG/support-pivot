package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.jira.JiraSearch;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
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
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
//    @fr.wseduc.security.SecuredAction("glpi.test.trigger") //TODO (voir comment faire)
    //todo rajouter la save du du ticket en mongo
    public void updateTicketJira(final HttpServerRequest request) {
        final String idJira = request.params().get(Field.IDJIRA);
        tryUpdateJira(idJira)
                .onSuccess(event -> Renders.renderJson(request, new JsonObject().put(Field.RESULT, event)))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::updateTicketJira] Fail update ticket by Jira %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    Renders.renderError(request);
                });
    }

    public Future<Void> tryUpdateJira(String idJira){
        JiraSearch jiraSearch = new JiraSearch().setIdJira(idJira);
        JsonObject jsonObject = new JsonObject();
        Promise<Void> promise = Promise.promise();
        routerService.getPivotTicket(EndpointFactory.getJiraEndpoint(), jiraSearch)
                .compose(pivotTicket -> routerService.setPivotTicket(EndpointFactory.getPivotEndpoint(), pivotTicket))
                .compose(supportTicket -> {
                    jsonObject.put(Field.RESULT, supportTicket.toJson());
                    return ServiceManager.getInstance().getMongoService().saveTicket(Field.UPDATETICKETJIRA, supportTicket.toJson());
                });
        promise.complete();
        return promise.future();
    }

    @BusAddress("supportpivot.updateJira")
    @SecuredAction("supportpivot.updateJira")
    public void busEndpointUpdateTicketJira(final Message<JsonObject> message) {
        final String idJira = message.body().getString(Field.IDJIRA);
        tryUpdateJira(idJira)
                .onSuccess(event -> message.reply(new JsonObject().put(Field.RESULT, event)))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::busEndpointUpdateTicketJira] Fail update ticket by Jira %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    message.reply(new JsonObject().put(Field.ERRORMESSAGE, error.getMessage()));
                });
    }
}
