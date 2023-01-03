package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.jira.JiraSearch;
import fr.openent.supportpivot.model.lde.LdeTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by bertinettia on 12/08/2019.
 * <p>
 * Controller for glpi
 */

public class LdeController extends ControllerHelper {
    private final RouterService routerService;

    protected static final Logger log = LoggerFactory.getLogger(LdeController.class);

    public LdeController() {
        this.routerService = ServiceManager.getInstance().getRouterService();
    }

    @Get("/lde/tickets")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getListeTicketsLDE(final HttpServerRequest request) {
        String date = request.params().get(Field.DATE);
        JiraSearch jiraSearch = new JiraSearch().setDate(date);
        routerService.getPivotTicketList(EndpointFactory.getJiraEndpoint(), jiraSearch)
                .onSuccess(pivotTicketList -> {
                    List<LdeTicket> ldeTicketList = pivotTicketList.stream()
                            .map(LdeTicket::new)
                            .map(LdeTicket::listFormat)
                            .collect(Collectors.toList());
                    Renders.renderJson(request, IModelHelper.toJsonArray(ldeTicketList));
                })
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getListeTicketsLDE] GET /lde/tickets failed : %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    Renders.renderError(request);
                });
    }

    @Get("/lde/ticket/:id")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getTicketLDE(final HttpServerRequest request) {
        String id_param_value = request.params().get(Field.ID);
        JiraSearch jiraSearch = new JiraSearch().setIdJira(id_param_value);
        routerService.getPivotTicket(EndpointFactory.getJiraEndpoint(), jiraSearch)
                .onSuccess(pivotTicket -> Renders.renderJson(request, new LdeTicket(pivotTicket).toJson()))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getTicketLDE] GET /lde/ticket/%s failed : %s",
                            this.getClass().getSimpleName(), id_param_value, error.getMessage()));
                    Renders.badRequest(request);
                });
    }

    @Put("/lde/ticket")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    //todo rajouter la save du du ticket en mongo
    public void putTicketLDE(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            routerService.setPivotTicket(EndpointFactory.getJiraEndpoint(), new PivotTicket(body))
                    .onSuccess(jiraTicket -> Renders.renderJson(request, jiraTicket.toJson()))
                    .onFailure(error -> {
                        log.error(String.format("[SupportPivot@%s::getTicketLDE] PUT /lde/ticket/ failed : %s",
                                this.getClass().getSimpleName(), error.getMessage()));
                        Renders.badRequest(request);
                    });
        });
    }
}
