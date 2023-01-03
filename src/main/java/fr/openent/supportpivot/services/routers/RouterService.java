package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.ISearchTicket;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;

import java.util.List;

public interface RouterService {
    <P extends IPivotTicket, S extends ISearchTicket> Future<PivotTicket> getPivotTicket(Endpoint<P, S> endpoint, S ticket);

    <P extends IPivotTicket, S extends ISearchTicket> Future<List<PivotTicket>> getPivotTicketList(Endpoint<P, S> endpoint, S ticket);

    <P extends IPivotTicket, S extends ISearchTicket> Future<P> setPivotTicket(Endpoint<P, S> endpoint, PivotTicket ticket);
}
