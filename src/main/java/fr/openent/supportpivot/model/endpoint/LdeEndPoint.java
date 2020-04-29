package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

public class LdeEndPoint extends AbstractEndpoint {


    protected static final Logger log = LoggerFactory.getLogger(LdeEndPoint.class);

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        checkTicketData(ticketData, result -> {
            if (result.isRight()) {
                PivotTicket ticket = new PivotTicket();
                ticket.setJsonObject(ticketData);
                handler.handle(Future.succeededFuture(ticket));
            } else {
                handler.handle(Future.failedFuture(result.left().toString()));
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.succeededFuture(ticket));
    }

    public void sendBack(PivotTicket ticket, Handler<AsyncResult<JsonObject>> handler)  {
        handler.handle(Future.succeededFuture(prepareJson(ticket)));
    }

    private JsonObject prepareJson(PivotTicket pivotTicket) {
        JsonObjectSafe ticket = new JsonObjectSafe();
        ticket.put(PivotTicket.IDJIRA_FIELD, pivotTicket.getJiraId());
        ticket.putSafe(PivotTicket.IDEXTERNAL_FIELD, pivotTicket.getExternalId());
        ticket.putSafe(PivotTicket.TITLE_FIELD, pivotTicket.getTitle());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        Instant createdDate = Instant.parse(pivotTicket.getRawCreatedAt());
        ticket.put(PivotTicket.RAWDATE_CREA_FIELD, formatter.format(createdDate));
        Instant updatedDate = Instant.parse(pivotTicket.getRawUpdatedAt());
        ticket.put(PivotTicket.RAWDATE_UPDATE_FIELD, formatter.format(updatedDate));
        return ticket;
    }

    public void prepareJsonList(List<PivotTicket> pivotTickets, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray jsonTickets = new JsonArray();
        for (PivotTicket pivotTicket : pivotTickets) {
            jsonTickets.add(prepareJson(pivotTicket));
        }
        handler.handle(Future.succeededFuture(jsonTickets));
    }

    private void checkTicketData(JsonObject ticketData, Handler<Either> handler) {
        //TODO CONTROLE DE FORMAT ICI
        handler.handle(new Either.Right<>(null));
       // handler.handle(new Either.Left<>("Bad Format :"));
    }




}
