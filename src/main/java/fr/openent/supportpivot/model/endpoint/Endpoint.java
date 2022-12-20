package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface Endpoint {

    String ENDPOINT_JIRA = "jira";
    String ENDPOINT_ENT = "ent";

    /**
     * Triggers ticket recuperation for this endpoint.
     * Might not do anything if the endpoint does not use trigger mecanism.
     * @param data Useful data for trigger. Might be an empty json object, but not null
     */
    Future<List<PivotTicket>> getPivotTicket(JsonObject data);

    /**
     * Process an incoming ticket from that endpoint.
     * @param ticketData Ticket data
     */
    Future<PivotTicket> process(JsonObject ticketData);

    /**
     * Process an existing ticket to send to that endpoint.
     * @param ticket Ticket data
     */
    Future<PivotTicket> send(PivotTicket ticket);
}
