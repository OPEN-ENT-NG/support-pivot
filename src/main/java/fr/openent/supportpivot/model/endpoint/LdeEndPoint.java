package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LdeEndPoint extends AbstractEndpoint {


    protected static final Logger log = LoggerFactory.getLogger(LdeEndPoint.class);

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        checkTicketData(ticketData, result -> {
            if (result.isRight()) {
                PivotTicket ticket = new PivotTicket(ticketData);
                handler.handle(Future.succeededFuture(ticket));
            } else {
                log.error(String.format("[SupportPivot@%s::process] Fail to process %s", this.getClass().getName(), EitherHelper.getOrNullLeftMessage(result)));
                handler.handle(Future.failedFuture(result.left().toString()));
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.succeededFuture(ticket));
    }

    public void sendBack(PivotTicket ticket, Handler<AsyncResult<JsonObject>> handler)  {
        handler.handle(Future.succeededFuture(prepareJson(ticket, true)));
    }

    //TODO use LdeTicket model
    private JsonObject prepareJson(PivotTicket pivotTicket, boolean isComplete) {
        PivotTicket pivotTicketClone = pivotTicket.clone();
        JsonObject ticket = isComplete ? pivotTicketClone.toJson() : new JsonObject();
        ticket.put(Field.ID_JIRA, pivotTicketClone.getIdJira());
        if(pivotTicketClone.getIdExterne() != null) ticket.put(Field.ID_EXTERNE, pivotTicketClone.getIdExterne());
        if(pivotTicketClone.getTitre() != null) ticket.put(Field.TITRE, pivotTicketClone.getTitre());
        DateTimeFormatter inFormatter = DateTimeFormatter.ofPattern(DateHelper.DATE_FORMAT_PARSE_UTC);
        DateTimeFormatter outFormatter = DateTimeFormatter.ofPattern(DateHelper.SQL_FORMAT_WITHOUT_SEPARATOR);
        ZonedDateTime createdDate = inFormatter.parse(pivotTicketClone.getCreation(), ZonedDateTime::from);
        ticket.put(Field.CREATION, outFormatter.format(createdDate));
        ZonedDateTime updatedDate = inFormatter.parse(pivotTicketClone.getMaj(), ZonedDateTime::from);
        ticket.put(Field.MAJ, outFormatter.format(updatedDate));

        // Remove endlines from base64 before sending to LDE
        JsonArray filteredPjs = new JsonArray();
        for(PivotPJ pivotPJ : pivotTicketClone.getPj()) {
            pivotPJ.setContenu(pivotPJ.getContenu().replace("\r\n", ""));
            filteredPjs.add(pivotPJ.toJson());
        }
        ticket.put(Field.PJ, filteredPjs);
        return ticket;
    }

    public void prepareJsonList(List<PivotTicket> pivotTickets, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray jsonTickets = new JsonArray();
        for (PivotTicket pivotTicket : pivotTickets) {
            jsonTickets.add(prepareJson(pivotTicket, false));
        }
        handler.handle(Future.succeededFuture(jsonTickets));
    }

    private void checkTicketData(JsonObject ticketData, Handler<Either> handler) {
        //TODO CONTROLE DE FORMAT ICI
        handler.handle(new Either.Right<>(null));
    }
}
