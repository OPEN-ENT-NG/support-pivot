package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.model.lde.LdeTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class LdeEndPoint extends AbstractEndpoint {

    protected static final Logger log = LoggerFactory.getLogger(LdeEndPoint.class);

    @Override
    public Future<PivotTicket> process(JsonObject ticketData) {
        Promise<PivotTicket> promise = Promise.promise();
        checkTicketData(ticketData, result -> {
            if (result.isRight()) {
                PivotTicket ticket = new PivotTicket(ticketData);
                promise.complete(ticket);
            } else {
                log.error(String.format("[SupportPivot@%s::process] Fail to process %s", this.getClass().getSimpleName(), EitherHelper.getOrNullLeftMessage(result)));
                promise.fail(EitherHelper.getOrNullLeftMessage(result));
            }
        });

        return promise.future();
    }

    @Override
    public Future<PivotTicket> send(PivotTicket ticket) {
        return Future.succeededFuture(ticket);
    }

    public void sendBack(PivotTicket ticket, Handler<AsyncResult<LdeTicket>> handler)  {
        handler.handle(Future.succeededFuture(prepareJson(ticket)));
    }

    private LdeTicket prepareJson(PivotTicket pivotTicket) {
        return new LdeTicket(pivotTicket);
    }

    public List<JsonObject> prepareJsonList(List<PivotTicket> pivotTickets) {
        return pivotTickets.stream().map(LdeTicket::new).map(LdeTicket::toJson).collect(Collectors.toList());
    }

    private void checkTicketData(JsonObject ticketData, Handler<Either> handler) {
        //TODO CONTROLE DE FORMAT ICI
        handler.handle(new Either.Right<>(null));
    }
}
