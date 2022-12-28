package fr.openent.supportpivot.services.impl;

import fr.openent.supportpivot.helpers.FutureHelper;
import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.ISearchTicket;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.services.RouterService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CrifRouterService implements RouterService {
    private static final Logger log = LoggerFactory.getLogger(CrifRouterService.class);

    @Override
    public <P extends IPivotTicket, S extends ISearchTicket> Future<PivotTicket> getPivotTicket(Endpoint<P, S> endpoint, S searchTicket) {
        Promise<PivotTicket> promise = Promise.promise();

        endpoint.getPivotTicket(searchTicket)
                .compose(endpoint::toPivotTicket)
                .onSuccess(promise::complete)
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::toPivotTicket] Fail to get ticket from %s %s",
                            this.getClass().getSimpleName(), endpoint.getName(), error.getMessage()));
                    promise.fail(error);
                });

        return promise.future();
    }

    @Override
    public <P extends IPivotTicket, S extends ISearchTicket> Future<List<PivotTicket>> getPivotTicketList(Endpoint<P, S> endpoint, S searchTicket) {
        Promise<List<PivotTicket>> promise = Promise.promise();

        endpoint.getPivotTicketList(searchTicket)
                .compose(ticketList -> {
                    List<Future<PivotTicket>> pivotTicketFutureList = ticketList.stream()
                            .map(endpoint::toPivotTicket)
                            .collect(Collectors.toList());
                    return FutureHelper.join(pivotTicketFutureList);
                })
                .onSuccess(pivotTicketList -> promise.complete(pivotTicketList.list()))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::toPivotTicket] Fail to get ticket from %s %s",
                            this.getClass().getSimpleName(), endpoint.getName(), error.getMessage()));
                    promise.fail(error);
                });

        return promise.future();
    }

    @Override
    public <P extends IPivotTicket, S extends ISearchTicket> Future<P> setPivotTicket(Endpoint<P, S> endpoint, PivotTicket ticket) {
        Promise<P> promise = Promise.promise();

        endpoint.setTicket(ticket)
                .onSuccess(promise::complete)
                .onFailure(promise::fail);

        return promise.future();
    }
}
