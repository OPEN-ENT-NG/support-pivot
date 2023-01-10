package fr.openent.supportpivot.services;

import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.ISearchTicket;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;

import java.util.List;

public interface RouterService {
    /**
     * Allows to search an IPivotTicket from an ISearchTicket in an Endpoint
     *
     * @param endpoint the endpoint that will allow us to search for a ticket
     * @param searchTicket the search object containing the information allows us to filter and carry out our search
     * @param <P> the type of ticket to use for the endpoint
     * @param <S> the type of the search object
     * @return a future complete with the found ticket
     */
    <P extends IPivotTicket, S extends ISearchTicket> Future<PivotTicket> getPivotTicket(Endpoint<P, S> endpoint, S searchTicket);

    /**
     * Allows to search a list of IPivotTicket from an ISearchTicket in an Endpoint
     *
     * @param endpoint the endpoint that will allow us to search for a ticket
     * @param searchTicket the search object containing the information allows us to filter and carry out our search
     * @param <P> the type of ticket to use for the endpoint
     * @param <S> the type of the search object
     * @return a future complete with the list of found ticket
     */
    <P extends IPivotTicket, S extends ISearchTicket> Future<List<PivotTicket>> getPivotTicketList(Endpoint<P, S> endpoint, S searchTicket);

    /**
     * Send an IPivotTicket to an external platform using an endpoint
     *
     * @param endpoint the endpoint that will allow us to send a ticket
     * @param ticket the ticket we want to send
     * @param <P> the type of ticket to use for the endpoint
     * @param <S> the type of the search object
     * @return a future complete with the ticket convert to <P> type
     */
    <P extends IPivotTicket, S extends ISearchTicket> Future<P> setPivotTicket(Endpoint<P, S> endpoint, PivotTicket ticket);
}
