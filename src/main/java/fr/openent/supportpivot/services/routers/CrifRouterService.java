package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.helpers.AsyncResultHelper;
import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.endpoint.LdeEndPoint;
import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import fr.openent.supportpivot.model.lde.LdeTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import fr.openent.supportpivot.services.MongoService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static fr.openent.supportpivot.constants.PivotConstants.*;

public class CrifRouterService implements RouterService {

    private static final Logger log = LoggerFactory.getLogger(CrifRouterService.class);
    private final JiraEndpoint jiraEndpoint;
    private LdeEndPoint ldeEndpoint;
    public MongoService mongoService;

    public CrifRouterService(HttpClientService httpClientService, JiraService jiraService) {
        jiraEndpoint = EndpointFactory.getJiraEndpoint(httpClientService, jiraService);
        ldeEndpoint = EndpointFactory.getLdeEndpoint();
        mongoService = new MongoService(PIVOT);
    }

    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (SOURCES.LDE.toString().equals(source)) {
            if (ticket.getIdJira() != null && !ticket.getIdJira().isEmpty()) {
                jiraEndpoint.send(ticket, jiraEndpointSendResult -> {
                    if (jiraEndpointSendResult.succeeded()) {
                        handler.handle(Future.succeededFuture(jiraEndpointSendResult.result()));
                    } else {
                        log.error(String.format("[SupportPivot@%s::dispatchTicket] Fail to dispatch ticket %s",
                                this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(jiraEndpointSendResult)));
                        handler.handle(Future.failedFuture(jiraEndpointSendResult.cause()));
                    }
                });
            } else {
                log.error(String.format("[SupportPivot@%s::dispatchTicket] %s is mandatory for IDF router.", this.getClass().getSimpleName(), Field.ID_JIRA));
                handler.handle(Future.failedFuture(Field.ID_JIRA + " is mandatory for IDF router."));
            }
        } else {
            if (ticket.getIdJira() == null) {
                jiraEndpoint.send(ticket, jiraEndpointSendResult -> {
                    if (jiraEndpointSendResult.succeeded()) {
                        handler.handle(Future.succeededFuture(jiraEndpointSendResult.result()));
                    } else {
                        log.error(String.format("[SupportPivot@%s::dispatchTicket] Fail to dispatch ticket %s",
                                this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(jiraEndpointSendResult)));
                        handler.handle(Future.failedFuture(jiraEndpointSendResult.cause()));
                    }
                });
            } else {
                log.error(String.format("[SupportPivot@%s::dispatchTicket] %s is mandatory for IDF router.", this.getClass().getSimpleName(), Field.ID_JIRA));
                handler.handle(Future.failedFuture(Field.ID_JIRA + " is mandatory for IDF router."));
            }
        }
    }


    //Todo use future and return Pivot ticket
    @Override
    public void toPivotTicket(String source, JsonObject ticketdata, Handler<AsyncResult<JsonObject>> handler) {
        if (SOURCES.LDE.toString().equals(source)) {
            mongoService.saveTicket(ATTRIBUTION_LDE, ticketdata);
            ldeEndpoint.process(ticketdata, ldeEndpointProcessResult -> {
                if (ldeEndpointProcessResult.succeeded()) {
                    dispatchTicket(source, ldeEndpointProcessResult.result(), pivotTicket -> {
                        if (pivotTicket.succeeded()) {
                            handler.handle(Future.succeededFuture(pivotTicket.result().toJson()));
                        } else {
                            handler.handle(Future.failedFuture(pivotTicket.cause()));
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture(ldeEndpointProcessResult.cause()));
                }

            });
        } else {
            mongoService.saveTicket(ATTRIBUTION_ENT, ticketdata.getJsonObject(ISSUE, new JsonObject()));
            PivotTicket ticket = new PivotTicket(ticketdata.getJsonObject(PivotConstants.ISSUE));
            dispatchTicket(source, ticket, dispatchJiraResult -> {
                if (dispatchJiraResult.succeeded()) {
                    handler.handle(Future.succeededFuture(dispatchJiraResult.result().toJson()));
                } else {
                    handler.handle(Future.failedFuture(dispatchJiraResult.cause()));
                }
            });
        }
    }

    @Override
    public void readTickets(String source, JsonObject data, Handler<AsyncResult<List<LdeTicket>>> handler) {
        if (SOURCES.LDE.toString().equals(source)) {
            String type = data == null ? "list" : data.getString("type", "");
            String minDate = data == null ? null : data.getString("date");
            if (type.equals("list")) {
                getTicketListFromJira(minDate, jiraResult -> {
                    if (jiraResult.failed()) {
                        log.error(String.format("[SupportPivot@%s::readTickets] Fail to read ticket %s",
                                this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(jiraResult)));
                        handler.handle(Future.failedFuture(jiraResult.cause()));
                    } else {
                        handler.handle(Future.succeededFuture(ldeEndpoint.prepareJsonList(jiraResult.result())));
                    }
                });
            } else {
                ldeEndpoint.process(data, ldeEndpointProcessResult -> {
                    if (ldeEndpointProcessResult.succeeded()) {
                        JsonObject pivotTicket = ldeEndpointProcessResult.result().toJson();
                        jiraEndpoint.process(pivotTicket, jiraEndpointProcessResult -> {
                            if (jiraEndpointProcessResult.succeeded()) {
                                PivotTicket pivotFormatTicket = jiraEndpointProcessResult.result();
                                ldeEndpoint.sendBack(pivotFormatTicket, ldeFormatTicketResult -> {
                                    if (ldeFormatTicketResult.succeeded()) {
                                        handler.handle(Future.succeededFuture(Collections.singletonList(ldeFormatTicketResult.result())));
                                    } else {
                                        log.error(String.format("[SupportPivot@%s::readTickets] Fail to sendBack %s",
                                                this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(ldeFormatTicketResult)));
                                        handler.handle(Future.failedFuture(ldeFormatTicketResult.cause()));
                                    }
                                });
                            } else {
                                log.error(String.format("[SupportPivot@%s::readTickets] Fail to jiraEndpoint.process %s",
                                        this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(jiraEndpointProcessResult)));
                                handler.handle(Future.failedFuture(jiraEndpointProcessResult.cause()));
                            }
                        });
                    } else {
                        log.error(String.format("[SupportPivot@%s::readTickets] Fail to ldeEndpoint.process %s",
                                this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(ldeEndpointProcessResult)));
                        handler.handle(Future.failedFuture(ldeEndpointProcessResult.cause()));
                    }
                });
            }
        } else {
            handler.handle(Future.failedFuture(source + " is an unsupported value for IDF router."));
        }
    }

    private void getTicketListFromJira(String minDate, Handler<AsyncResult<List<PivotTicket>>> handler) {
        JsonObjectSafe data = new JsonObjectSafe();
        data.put(JiraConstants.ATTRIBUTION_FILTERNAME, JiraConstants.ATTRIBUTION_FILTER_LDE);
        data.put(JiraConstants.ATTRIBUTION_FILTER_CUSTOMFIELD, JiraConstants.IDEXTERNAL_FIELD);
        data.putSafe(JiraConstants.ATTRIBUTION_FILTER_DATE, minDate);
        jiraEndpoint.getPivotTicket(data, handler);
    }
}
