package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.model.ent.SupportSearch;
import fr.openent.supportpivot.model.ent.SupportTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;


public class SupportEndpoint implements Endpoint<SupportTicket, SupportSearch> {
    private final EventBus eventBus;
    public final static String BUS_SEND = "support.update.bugtracker";

    private static final Logger log = LoggerFactory.getLogger(SupportEndpoint.class);

    public SupportEndpoint(Vertx vertx) {
        this.eventBus = getEventBus(vertx);
    }

    @Override
    public Future<SupportTicket> getPivotTicket(SupportSearch iPivotTicket) {
        throw new RuntimeException("Unsupported operation");
    }

    @Override
    public Future<List<SupportTicket>> getPivotTicketList(SupportSearch searchTicket) {
        throw new RuntimeException("Unsupported operation");
    }

    @Override
    public Future<SupportTicket> setTicket(PivotTicket ticket) {
        Promise<SupportTicket> promise = Promise.promise();
        JsonObject messageEventBus = new JsonObject()
                .put(Field.ACTION, Field.CREATE)
                .put(Field.ISSUE, ticket.toJson());
        eventBus.request(BUS_SEND, messageEventBus,
                handlerToAsyncHandler(message -> {
                    if (Field.OK.equals(message.body().getString(Field.STATUS))) {
                        log.info(String.format("[SupportPivot@%s::send] %s", this.getClass().getSimpleName(), message.body()));
                        //Todo voir si c'est normal que l'on renvoit un nouveau ticket
                        promise.complete(new SupportTicket());
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to send to support %s",
                                this.getClass().getSimpleName(), message.body().toString()));
                        promise.fail(message.body().toString());
                    }
                })
        );

        return promise.future();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Future<PivotTicket> toPivotTicket(SupportTicket supportTicket) {
        throw new RuntimeException("Unsupported operation");
    }
}
