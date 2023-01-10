package fr.openent.supportpivot.helpers;

import fr.wseduc.webutils.Either;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class FutureHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FutureHelper.class);

    private FutureHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static <L, R> Handler<Either<L, R>> handlerEitherPromise(Promise<R> promise) {
        return handlerEitherPromise(promise, "");
    }

    public static <L, R> Handler<Either<L, R>> handlerEitherPromise(Promise<R> promise, String errorMessage) {
        return event -> {
            if (event.isRight()) {
                promise.complete(event.right().getValue());
            } else {
                String message = String.format("[SupportPivot@%s::handlerEitherPromise]: %s %s",
                        FutureHelper.class.getSimpleName(), errorMessage, event.left().getValue());
                LOGGER.error(message);
                promise.fail(event.left().getValue().toString());
            }
        };
    }

    public static <T> CompositeFuture all(List<Future<T>> futures) {
        return CompositeFutureImpl.all(futures.toArray(new Future[futures.size()]));
    }

    public static <T> CompositeFuture join(List<Future<T>> futures) {
        return CompositeFutureImpl.join(futures.toArray(new Future[futures.size()]));
    }

}