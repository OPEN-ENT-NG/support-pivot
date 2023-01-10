package fr.openent.supportpivot.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Class used to avoid inserting keys with 'null' in value
 */
public class JsonObjectSafe extends JsonObject {
    public JsonObjectSafe putSafe(String key, String value) {
        if(value != null) {
            this.put(key, value);
        }
        return this;
    }
    public JsonObjectSafe putSafe(String key, JsonObject value) {
        if(value != null) {
            this.put(key, value);
        }
        return this;
    }
    public JsonObjectSafe putSafe(String key, JsonArray value) {
        if(value != null) {
            this.put(key, value);
        }
        return this;
    }
}
