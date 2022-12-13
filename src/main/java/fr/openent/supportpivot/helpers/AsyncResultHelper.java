package fr.openent.supportpivot.helpers;

import io.vertx.core.AsyncResult;

public class AsyncResultHelper {
    public static String getOrNullFailMessage(AsyncResult<?> asyncResult) {
        return (asyncResult.cause() == null) ? "null" : (asyncResult.cause().getMessage() == null) ?
                asyncResult.cause().toString() : asyncResult.cause().getMessage();
    }
}
