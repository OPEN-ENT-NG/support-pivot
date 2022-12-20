package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.model.pivot.PivotTicket;
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
    public Future<PivotTicket> process(JsonObject ticketData) {
        final JsonObject issue = ticketData.getJsonObject(Field.ISSUE);
        PivotTicket ticket = new PivotTicket(issue);
        return Future.succeededFuture(ticket);
    }

    @Override
    public Future<PivotTicket> send(PivotTicket ticket) {
        Promise<PivotTicket> promise = Promise.promise();
        JsonObject messageEventBus = new JsonObject()
                .put(Field.ACTION, Field.CREATE)
                .put(Field.ISSUE, ticket.toJson());
        eventBus.request(PivotConstants.BUS_SEND, messageEventBus,
                handlerToAsyncHandler(message -> {
                    if (PivotConstants.ENT_BUS_OK_STATUS.equals(message.body().getString(Field.STATUS))) {
                        log.info(String.format("[SupportPivot@%s::send] %s", this.getClass().getSimpleName(), message.body()));
                        //Todo voir si c'est normal que l'on renvoit un nouveau ticket
                        promise.complete(new PivotTicket());
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to send to support %s",
                                this.getClass().getSimpleName(), message.body().toString()));
                        promise.fail(message.body().toString());
                    }
                })
        );

        return promise.future();
    }
}
