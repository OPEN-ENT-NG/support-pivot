package fr.openent.supportpivot.model.ent;

import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.IPivotTicket;
import io.vertx.core.json.JsonObject;

public class SupportTicket implements IPivotTicket, IModel<SupportTicket> {

    public SupportTicket(JsonObject jsonObject) {
    }

    public SupportTicket() {
    }

    @Override
    public JsonObject toJson() {
        return null;
    }
}
