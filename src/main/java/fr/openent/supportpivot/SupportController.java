/*
 *
 * Copyright (c) Mairie de Paris, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.supportpivot;

import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.impl.DefaultDemandeServiceImpl;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.email.EmailFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;


/**
 * Created by colenot on 07/12/2017.
 *
 * Controller for support pivot
 *
 * Exposed API
 * /demande : Register a demande from IWS
 * /testMail/:mail : Send a test mail to address in parameter
 * /demandeENT : Register a demande from Support module
 */
public class SupportController extends ControllerHelper{

    private DemandeService demandeService;

    @Override
    public void init(Vertx vertx, final JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        EmailFactory emailFactory = new EmailFactory(vertx, config);
        EmailSender emailSender = emailFactory.getSender();
        this.demandeService = new DefaultDemandeServiceImpl(vertx, config, emailSender);
    }

    /**
     * Webservice. Receive info from IWS
     */
    @Post("/demande")
    @SecuredAction("supportpivot.ws.demande")
    public void IWSEntryEndpoint(final HttpServerRequest request) {
        //TODO return errorcode 1 if non JSON body
        RequestUtils.bodyToJson(request, pathPrefix+"iwsticket", resource -> demandeService.treatTicketFromIWS(request, resource, getDefaultResponseHandler(request)));
    }

    /**
     * Get a default handler for HttpServerRequest with added info
     * @return handler with error code, error message and status
     */
    private Handler<Either<String, JsonObject>> getDefaultResponseHandler(final HttpServerRequest request){
        return  event -> {
                if (event.isRight()) {
                    Renders.renderJson(request, event.right().getValue(), 200);
                } else {
                    String errorCode = event.left().getValue();
                    String errorCodeMsg="";
                    if(errorCode.contains(";")){
                        errorCodeMsg=errorCode.substring(errorCode.indexOf(";")+1);
                        errorCode=errorCode.split(";")[0];
                    }
                    JsonObject error = new JsonObject()
                            .put("errorCode", errorCode)
                            .put("errorMessage", errorCodeMsg)
                            .put("status", "KO");
                    Renders.renderJson(request, error, 400);
                }
            };
    }

    /**
     * Internel webservice. Receive info from support module
     */
    @BusAddress("supportpivot.demande")
    public void busEndpoint(final Message<JsonObject> message) {
        final JsonObject issue = message.body().getJsonObject("issue");
        demandeService.treatTicketFromENT( issue, event -> {
            if(event.isRight()) {
                message.reply(new JsonObject().put("status", "ok")
                        .put("message", "invalid.action")
                        .put("issue", issue));
            } else {
                log.error("Supportpivot : error when trying send ticket from ENT" + event.left().getValue());
                message.reply(new JsonObject().put("status", "ko")
                    .put("message", event.left().getValue()));
            }
        });

    }

    /**
     * Webservice. Send an issue to specified mail with fictive info, for testing purpose
     */
    @Get("getMongoInfos/:request")
    @SecuredAction("supportpivot.ws.dbrequest")
    public void getMongoInfos(final HttpServerRequest request) {
        final String mailTo = request.params().get("request");
        demandeService.getMongoInfos(request, mailTo, getDefaultResponseHandler(request));
    }

    /**
     * Webservice. Send info updated from Jira to IWS
     */
    @Get("updateJira/:idjira")
    public void jiraUpdateEndpoint(final HttpServerRequest request) {
        final String idJira = request.params().get("idjira");
        demandeService.sendJiraTicketToIWS(request, idJira, getDefaultResponseHandler(request));
    }


}
