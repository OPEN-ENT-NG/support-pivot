package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.enums.SourceEnum;
import fr.openent.supportpivot.helpers.AsyncResultHelper;
import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.endpoint.LdeEndPoint;
import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import fr.openent.supportpivot.services.MongoService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class CrifRouterService implements RouterService {
    private final static String COLLECTION_MONGO = "pivot";
    private static final Logger log = LoggerFactory.getLogger(CrifRouterService.class);
    private final JiraEndpoint jiraEndpoint;
    private LdeEndPoint ldeEndpoint;
    public MongoService mongoService;

    public CrifRouterService(HttpClientService httpClientService, JiraService jiraService) {
        jiraEndpoint = EndpointFactory.getJiraEndpoint(httpClientService, jiraService);
        ldeEndpoint = EndpointFactory.getLdeEndpoint();
        mongoService = new MongoService(COLLECTION_MONGO);
    }

    @Override
    public Future<PivotTicket> dispatchTicket(String source, PivotTicket ticket) {
        Promise<PivotTicket> promise = Promise.promise();

        if (ticket.getIdJira() != null && !ticket.getIdJira().isEmpty()) {
            jiraEndpoint.send(ticket)
                    .onSuccess(promise::complete)
                    .onFailure(error -> {
                        log.error(String.format("[SupportPivot@%s::dispatchTicket] Fail to dispatch ticket %s",
                                this.getClass().getSimpleName(), error.getMessage()));
                        promise.fail(error);
                    });
        } else {
            log.error(String.format("[SupportPivot@%s::dispatchTicket] %s is mandatory for IDF router.", this.getClass().getSimpleName(), Field.ID_JIRA));
            return Future.failedFuture(Field.ID_JIRA + " is mandatory for IDF router.");
        }

        return promise.future();
    }

    @Override
    public Future<PivotTicket> toPivotTicket(String source, JsonObject ticketdata) {
        Promise<PivotTicket> promise = Promise.promise();

        if (SourceEnum.LDE.toString().equals(source)) {
            mongoService.saveTicket(Field.LDE, ticketdata)
                    .compose(unused -> ldeEndpoint.process(ticketdata))
                    .compose(pivotTicket -> dispatchTicket(source, pivotTicket))
                    .onSuccess(promise::complete)
                    .onFailure(error -> {
                        log.error(String.format("[SupportPivot@%s::toPivotTicket] Fail to get ticket from LDE %s",
                                this.getClass().getSimpleName(), error.getMessage()));
                        promise.fail(error);
                    });
        } else {
            mongoService.saveTicket(Field.ENT, ticketdata.getJsonObject(Field.ISSUE, new JsonObject()))
                    .compose(event -> {
                        PivotTicket ticket = new PivotTicket(ticketdata.getJsonObject(Field.ISSUE));
                        return dispatchTicket(source, ticket);
                    })
                    .onSuccess(promise::complete)
                    .onFailure(error -> {
                        log.error(String.format("[SupportPivot@%s::toPivotTicket] Fail to get ticket from %s %s",
                                this.getClass().getSimpleName(), source, error.getMessage()));
                        promise.fail(error);
                    });
        }

        return promise.future();
    }

    @Override
    public Future<List<JsonObject>> readTickets(String source, JsonObject data) {
        Promise<List<JsonObject>> promise = Promise.promise();

        if (SourceEnum.LDE.toString().equals(source)) {
            String type = data == null ? Field.LIST : data.getString(Field.TYPE, "");
            String minDate = data == null ? null : data.getString(Field.DATE);
            if (type.equals(Field.LIST)) {
                getTicketListFromJira(minDate)
                        .onSuccess(ldeTicketList -> promise.complete(ldeEndpoint.prepareJsonList(ldeTicketList)))
                        .onFailure(error -> {
                            log.error(String.format("[SupportPivot@%s::readTickets] Fail to read ticket %s",
                                    this.getClass().getSimpleName(), error.getMessage()));
                            promise.fail(error);
                        });
            } else {
                //Todo Why ldeEndpoint.process and jiraEndpoint.process?
                ldeEndpoint.process(data)
                        .compose(pivotTicket -> jiraEndpoint.process(pivotTicket.toJson()))
                        .onSuccess(pivotTicket -> {
                            ldeEndpoint.sendBack(pivotTicket, ldeFormatTicketResult -> {
                                if (ldeFormatTicketResult.succeeded()) {
                                    promise.complete(Collections.singletonList(ldeFormatTicketResult.result().toJson()));
                                } else {
                                    log.error(String.format("[SupportPivot@%s::readTickets] Fail to sendBack %s",
                                            this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(ldeFormatTicketResult)));
                                    promise.fail(ldeFormatTicketResult.cause());
                                }
                            });
                        })
                        .onFailure(error -> {
                            log.error(String.format("[SupportPivot@%s::readTickets] Fail to process %s",
                                    this.getClass().getSimpleName(), error.getMessage()));
                            promise.fail(error);
                        });
            }
        } else {
            return Future.failedFuture(source + " is an unsupported value for IDF router.");
        }

        return promise.future();
    }

    private Future<List<PivotTicket>> getTicketListFromJira(String minDate) {
        JsonObjectSafe data = new JsonObjectSafe();
        data.put(JiraConstants.ATTRIBUTION_FILTERNAME, JiraConstants.ATTRIBUTION_FILTER_LDE);
        data.put(JiraConstants.ATTRIBUTION_FILTER_CUSTOMFIELD, JiraConstants.IDEXTERNAL_FIELD);
        data.putSafe(JiraConstants.ATTRIBUTION_FILTER_DATE, minDate);
        return jiraEndpoint.getPivotTicket(data);
    }
}
