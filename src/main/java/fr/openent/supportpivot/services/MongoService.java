package fr.openent.supportpivot.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface MongoService {
    Future<Void> saveTicket(String source, JsonObject jsonTicket);

    Future<JsonObject> getMongoInfos(String mailTo);
}
