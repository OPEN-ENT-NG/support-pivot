package fr.openent.supportpivot.services;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.helpers.FutureHelper;
import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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
    public Future<Void> saveTicket(String source, JsonObject jsonTicket) {
        Promise<Void> promise = Promise.promise();

        DateFormat dateFormat = new SimpleDateFormat(DateHelper.DATE_FORMAT_PARSE_UTC);
        Date date = new Date();
        jsonTicket.put(Field.SOURCE, source);
        jsonTicket.put(Field.DATE, dateFormat.format(date));

        mongo.insert(mongoCollection, jsonTicket, MongoDbResult.validActionResultHandler(event -> {
            if (event.isLeft()) {
                String message = EitherHelper.getOrNullLeftMessage(event);
                log.error(String.format("[SupportPivot@%s::saveTicket] Could not save json to mongoDB %s", this.getClass().getSimpleName(), message));
                promise.fail(message);
            } else {
                promise.complete();
            }
        }));

        return promise.future();
    }

    public Future<JsonObject> getMongoInfos(String mailTo) {
        Promise<JsonObject> promise = Promise.promise();
        try {
            JsonObject req = new JsonObject(java.net.URLDecoder.decode(mailTo, Field.ENCODE_UTF_8));
            mongo.find(mongoCollection, req, MongoDbResult.validResultHandler(FutureHelper.handlerEitherPromise(promise)));
        } catch(Exception e) {
            return Future.failedFuture(String.format("[SupportPivot@%s::getMongoInfos] Malformed json", this.getClass().getSimpleName()));
        }

        return promise.future();
    }

}
