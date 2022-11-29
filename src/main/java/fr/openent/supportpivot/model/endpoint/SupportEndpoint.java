package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;


class SupportEndpoint extends  AbstractEndpoint {

    private EventBus eventBus;

    private static final Logger log = LoggerFactory.getLogger(SupportEndpoint.class);

    SupportEndpoint(Vertx vertx) {
        this.eventBus = getEventBus(vertx);
    }


    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        final JsonObject issue = ticketData.getJsonObject(Field.ISSUE);
        PivotTicket ticket = new PivotTicket();
        ticket.setJsonObject(issue);
        handler.handle(Future.succeededFuture(ticket));
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
         try {
                    eventBus
                            .send(PivotConstants.BUS_SEND, new JsonObject()
                                            .put(Field.ACTION, Field.CREATE)
                                            .put(Field.ISSUE, ticket.getJsonTicket()),
                                    handlerToAsyncHandler(message -> {
                                        if (PivotConstants.ENT_BUS_OK_STATUS.equals(message.body().getString(Field.STATUS))) {
                                            log.info(message.body());
                                            handler.handle(Future.succeededFuture(new PivotTicket()));
                                        } else {
                                            handler.handle(Future.failedFuture(message.body().toString()));
                                        }
                                    })
                            );
                } catch (Error e) {
                    handler.handle(Future.failedFuture(e.getMessage()));
                }
    }
}
