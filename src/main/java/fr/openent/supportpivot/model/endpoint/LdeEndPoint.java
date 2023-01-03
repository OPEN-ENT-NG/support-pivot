package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.lde.LdeSearch;
import fr.openent.supportpivot.model.lde.LdeTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class LdeEndPoint implements Endpoint<LdeTicket, LdeSearch> {

    protected static final Logger log = LoggerFactory.getLogger(LdeEndPoint.class);

    @Override
    public Future<LdeTicket> getPivotTicket(LdeSearch iPivotTicket) {
        throw new RuntimeException("Unsupported operation");
    }

    @Override
    public Future<List<LdeTicket>> getPivotTicketList(LdeSearch searchTicket) {
        throw new RuntimeException("Unsupported operation");
    }

    @Override
    public Future<LdeTicket> setTicket(PivotTicket ticket) {
        throw new RuntimeException("Unsupported operation");
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Future<PivotTicket> toPivotTicket(LdeTicket ticket) {
        throw new RuntimeException("Unsupported operation");
    }
}
