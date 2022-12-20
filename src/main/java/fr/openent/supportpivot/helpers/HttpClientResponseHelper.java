package fr.openent.supportpivot.helpers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;

public class HttpClientResponseHelper {
    public static Future<Buffer> bodyHandler(HttpClientResponse response) {
        Promise<Buffer> promise = Promise.promise();
        response.bodyHandler(promise::complete);
        return promise.future();
    }
}
