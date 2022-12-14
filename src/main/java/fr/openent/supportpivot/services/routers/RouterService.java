package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface RouterService {


    void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler);

    void toPivotTicket(String source, JsonObject ticketdata,
                       Handler<AsyncResult<JsonObject>> handler);

    void readTickets(String source, JsonObject data, Handler<AsyncResult<JsonArray>> handler);
}
