package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface RouterService {

    Future<PivotTicket> dispatchTicket(String source, PivotTicket ticket);

    Future<PivotTicket> toPivotTicket(String source, JsonObject ticketdata);

    Future<List<JsonObject>> readTickets(String source, JsonObject data);
}
