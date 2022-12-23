package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.ISearchTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;

import java.util.List;

/**
 * @param <P> Object class representing the ticket model
 * @param <S> Object class representing the model to do a search
 */
public interface Endpoint<P extends IPivotTicket, S extends ISearchTicket> {

    /**
     * Allows you to retrieve a ticket thanks to an ISearch Ticket.
     *
     * @param searchTicket a ISearchTicket
     * @return a ticket in IPivotTicket format
     */
    Future<P> getPivotTicket(S searchTicket);

    /**
     * Allows you to retrieve a list of ticket thanks to an ISearch Ticket.
     *
     * @param searchTicket a ISearchTicket
     * @return a list of ticket in IPivotTicket format
     */
    Future<List<P>> getPivotTicketList(S searchTicket);

    /**
     * Permet d'envoyer un PivotTicket.
     *
     * @param ticket a PivotTicket
     * @return the ticket formated in IPivotTicket
     */
    Future<P> setTicket(PivotTicket ticket);

    /**
     * @return a name to identify the endpoint.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Convert a IPivotTicket to PivotTicket. Can be use if we need async function.
     *
     * @param ticket the ticket we want to convert
     * @return the ticket converted
     */
    Future<PivotTicket> toPivotTicket(P ticket);
}
