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

package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.helpers.AsyncResultHelper;
import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.services.MongoService;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

/**
 * Created by colenot on 07/12/2017.
 * Controller for support pivot
 * Exposed API
 * /demande : Register a demande from IWS
 * /testMail/:mail : Send a test mail to address in parameter
 * /demandeENT : Register a demande from Support module
 */
public class SupportController extends ControllerHelper {

    private DemandeService demandeService;
    private MongoService mongoService;
    private RouterService routerService;

    @Override
    public void init(Vertx vertx, final JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.getInstance();

        this.demandeService = serviceManager.getDemandeService();
        this.mongoService = serviceManager.getMongoService();
        this.routerService = serviceManager.getRouteurService();
    }
    /**
     * Get a default handler for HttpServerRequest with added info
     * @return handler with error code, error message and status
     */
    private Handler<Either<String, JsonObject>> getDefaultResponseHandler(final HttpServerRequest request) {
        return event -> {
            if (event.isRight()) {
                Renders.renderJson(request, event.right().getValue(), 200);
            } else {
                log.error(String.format("[SupportPivot@%s::getDefaultResponseHandler] %s",
                        this.getClass().getName(), EitherHelper.getOrNullLeftMessage(event)));
                String errorCode = event.left().getValue();
                String errorCodeMsg = "";
                if (errorCode.contains(";")) {
                    errorCodeMsg = errorCode.substring(errorCode.indexOf(";") + 1);
                    errorCode = errorCode.split(";")[0];
                }
                JsonObject error = new JsonObject()
                        .put(PivotConstants.ERRORCODE, errorCode)
                        .put(PivotConstants.ERRORMESSAGE, errorCodeMsg)
                        .put(PivotConstants.STATUS, PivotConstants.KO);
                Renders.renderJson(request, error, 400);
            }
        };
    }

    /**
     * Get a default handler for HttpServerRequest with added info
     * @return handler with error code, error message and status
     */
    private Handler<AsyncResult<JsonObject>> getDefaultResponseHandlerAsync(final HttpServerRequest request) {
        return result -> {
            if (result.succeeded()) {
                Renders.renderJson(request, result.result(), 200);
            } else {
                log.error(String.format("[SupportPivot@%s::getDefaultResponseHandler] %s",
                        this.getClass().getName(), AsyncResultHelper.getOrNullFailMessage(result)));
                String errorCode = result.cause().getMessage();
                String errorCodeMsg = "";
                if (errorCode.contains(";")) {
                    errorCodeMsg = errorCode.substring(errorCode.indexOf(";") + 1);
                    errorCode = errorCode.split(";")[0];
                }
                JsonObject error = new JsonObject()
                        .put(Field.ERRORCODE, errorCode)
                        .put(Field.ERRORMESSAGE, errorCodeMsg)
                        .put(Field.STATUS, Field.KO);
                Renders.renderJson(request, error, 400);
            }
        };
    }

    /**
     * Internel webservice. Receive info from support module
     */
    @BusAddress("supportpivot.demande")
    @SecuredAction("supportpivot.demande")
    public void busEndpoint(final Message<JsonObject> message) {
        JsonObject jsonMessage = message.body();
        this.routerService.toPivotTicket(Endpoint.ENDPOINT_ENT, jsonMessage, event -> {
            if (event.succeeded()) {
                message.reply(new JsonObject().put(Field.STATUS, Field.OK.toLowerCase())
                        .put(Field.MESSAGE, "invalid.action")
                        .put(Field.ISSUE, event.result()));
            } else {
                message.reply(new JsonObject().put(Field.STATUS, Field.KO.toLowerCase())
                        .put(Field.MESSAGE, event.cause().getMessage()));
            }
        });
    }

    /**
     * Webservice. Send an issue to specified mail with fictive info, for testing purpose
     */
    @Get("getMongoInfos/:request")
    @SecuredAction("supportpivot.ws.dbrequest")
    public void getMongoInfos(final HttpServerRequest request) {
        final String mailTo = request.params().get(Field.REQUEST);
        mongoService.getMongoInfos(mailTo, getDefaultResponseHandlerAsync(request));
    }

    @Get("config")
    @SecuredAction("supportpivot.ws.config")
    public void getConfig(final HttpServerRequest request) {
        renderJson(request, ConfigManager.getInstance().getPublicConfig());
    }

    @Get("updateJira/:idjira")
    public void jiraUpdateEndpoint(final HttpServerRequest request) {
        final String idJira = request.params().get(JiraConstants.ID_JIRA);
        demandeService.sendJiraTicketToSupport(request, idJira, getDefaultResponseHandler(request));
    }
}
