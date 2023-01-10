package fr.openent.supportpivot.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface MongoService {
    /**
     * Saves a ticket in Json format in the mongo database
     *
     * @param source the source from which the ticket originated
     * @param jsonTicket the ticket in json format
     * @return a future complete
     */
    Future<Void> saveTicket(String source, JsonObject jsonTicket);

    /**
     * Allows to obtain information from the mongo thanks to a request
     *
     * @param request the query that will be executed to obtain the desired information
     * @return the query results
     */
    Future<JsonObject> getMongoInfos(JsonObject request);
}
