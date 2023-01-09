package fr.openent.supportpivot.model.ent;

import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.json.JsonObject;

public class SupportTicket implements IPivotTicket, IModel<SupportTicket> {

    public SupportTicket(JsonObject jsonObject) {
    }

    public SupportTicket() {
    }

    public SupportTicket(PivotTicket ticket) {
    }

    @Override
    public JsonObject toJson() {
        return new JsonObject();
    }
}
