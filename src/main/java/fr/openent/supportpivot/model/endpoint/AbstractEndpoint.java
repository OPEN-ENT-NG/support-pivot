package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class AbstractEndpoint implements Endpoint {
    @Override
    public Future<List<PivotTicket>> getPivotTicket(JsonObject data) {
        return Future.failedFuture("Trigger is not allowed for " + getClass().getName());
    }

    @Override
    public Future<PivotTicket> process(JsonObject ticketData) {
        return Future.failedFuture("process is not allowed for " + getClass().getName());
    }

    @Override
    public Future<PivotTicket> send(PivotTicket ticket) {
        return Future.failedFuture("Send is not allowed for " + getClass().getName());
    }
}
