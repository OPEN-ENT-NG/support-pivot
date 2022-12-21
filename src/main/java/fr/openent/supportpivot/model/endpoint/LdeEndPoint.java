package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.model.lde.LdeTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class LdeEndPoint extends AbstractEndpoint {


    protected static final Logger log = LoggerFactory.getLogger(LdeEndPoint.class);

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        checkTicketData(ticketData, result -> {
            if (result.isRight()) {
                PivotTicket ticket = new PivotTicket(ticketData);
                handler.handle(Future.succeededFuture(ticket));
            } else {
                log.error(String.format("[SupportPivot@%s::process] Fail to process %s", this.getClass().getSimpleName(), EitherHelper.getOrNullLeftMessage(result)));
                handler.handle(Future.failedFuture(result.left().toString()));
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.succeededFuture(ticket));
    }

    public void sendBack(PivotTicket ticket, Handler<AsyncResult<LdeTicket>> handler)  {
        handler.handle(Future.succeededFuture(prepareJson(ticket)));
    }

    //TODO use LdeTicket model
    private LdeTicket prepareJson(PivotTicket pivotTicket) {
        return new LdeTicket(pivotTicket);
    }

    public List<LdeTicket> prepareJsonList(List<PivotTicket> pivotTickets) {
        return pivotTickets.stream().map(LdeTicket::new).collect(Collectors.toList());
    }

    private void checkTicketData(JsonObject ticketData, Handler<Either> handler) {
        //TODO CONTROLE DE FORMAT ICI
        handler.handle(new Either.Right<>(null));
    }
}
