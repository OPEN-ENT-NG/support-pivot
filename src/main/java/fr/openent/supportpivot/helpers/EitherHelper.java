package fr.openent.supportpivot.helpers;

import fr.wseduc.webutils.Either;

public class EitherHelper {
    public static String getOrNullLeftMessage(Either<String, ?> either) {
        return (either.left() != null && either.left().getValue() != null) ? either.left().getValue() : "null";
    }
}
