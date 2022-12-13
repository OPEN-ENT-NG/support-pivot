package fr.openent.supportpivot.services;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.EitherHelper;
import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.mongodb.MongoDbResult;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MongoService {

    private final MongoDb mongo;
    private final String mongoCollection;

    private final Logger log = LoggerFactory.getLogger(Supportpivot.class);

    public MongoService(String mongoCollection) {
        this.mongo = MongoDb.getInstance();
        this.mongoCollection = mongoCollection;
    }

    //Todo convert too future
    public void saveTicket(final String source, JsonObject jsonPivot) {
        DateFormat dateFormat = new SimpleDateFormat(DateHelper.DATE_FORMAT_PARSE_UTC);
        Date date = new Date();
        jsonPivot.put(Field.SOURCE, source);
        jsonPivot.put(Field.DATE, dateFormat.format(date));

        mongo.insert(mongoCollection, jsonPivot, MongoDbResult.validActionResultHandler(event -> {
            if (event.isLeft()) {
                String message = EitherHelper.getOrNullLeftMessage(event);
                log.error(String.format("[SupportPivot@%s::saveTicket] Could not save json to mongoDB %s", this.getClass().getName(), message));
            }
        }));
    }

    public void getMongoInfos(String mailTo, final Handler<AsyncResult<JsonObject>> handler) {
        try {
            JsonObject req = new JsonObject(java.net.URLDecoder.decode(mailTo, Field.ENCODE_UTF_8));
            mongo.find(mongoCollection, req,
                    jsonObjectMessage -> handler.handle(Future.succeededFuture(jsonObjectMessage.body())));
        } catch(Exception e) {
            handler.handle(Future.failedFuture("Malformed json"));
        }
    }

}
