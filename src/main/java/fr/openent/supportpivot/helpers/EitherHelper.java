package fr.openent.supportpivot.helpers;

import fr.wseduc.webutils.Either;

public class EitherHelper {
    private EitherHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Avoid an NPE when we want to access the error message of an Either
     */
    public static String getOrNullLeftMessage(Either<String, ?> either) {
        return (either.left() != null && either.left().getValue() != null) ? either.left().getValue() : "null";
    }
}
