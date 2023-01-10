package fr.openent.supportpivot.helpers;

import io.vertx.core.AsyncResult;

public class AsyncResultHelper {
    private AsyncResultHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Avoid an NPE when we want to access the error message of an AsyncResult
     */
    public static String getOrNullFailMessage(AsyncResult<?> asyncResult) {
        return (asyncResult.cause() == null) ? "null" : (asyncResult.cause().getMessage() == null) ?
                asyncResult.cause().toString() : asyncResult.cause().getMessage();
    }
}
