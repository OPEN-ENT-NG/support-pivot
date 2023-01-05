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

package fr.openent.supportpivot.services.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.ConfigField;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.helpers.HttpRequestHelper;
import fr.openent.supportpivot.helpers.StringHelper;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.jira.JiraComment;
import fr.openent.supportpivot.model.jira.JiraFields;
import fr.openent.supportpivot.model.jira.JiraSearch;
import fr.openent.supportpivot.model.jira.JiraTicket;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.model.status.config.EntStatusConfig;
import fr.openent.supportpivot.model.status.config.StatusConfigModel;
import fr.openent.supportpivot.services.JiraService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class JiraServiceImpl implements JiraService {

    private final Logger LOGGER = LoggerFactory.getLogger(JiraServiceImpl.class);
    private final URI jiraAbsUrl;
    private final String jiraProjectName;
    private final String defaultJiraTickettype;
    private final String defaultPriority;
    private final JsonArray jiraAllowedPriority;
    private final JsonArray jiraAllowedTickettype;
    private final Map<String, String> jiraField;
    private final List<EntStatusConfig> entStatusMapping;

    private static final Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static final Base64.Decoder decoder = Base64.getMimeDecoder();

    public static final List<String> PIVOT_PRIORITY_LEVEL = Arrays.asList(
            Field.MINEUR,
            Field.MAJEUR,
            Field.BLOQUANT);
    public static final int COMMENT_LENGTH = 4;

    public JiraServiceImpl() {
        ConfigModel config = ConfigManager.getInstance().getConfig();

        try {
            this.jiraAbsUrl = new URI(config.getJiraHost() + config.getJiraURL());
        } catch (URISyntaxException e) {
            LOGGER.error(String.format("[SupportPivot@%s::JiraServiceImpl] Bad parameter ent-core.json#jira-url %s", this.getClass().getSimpleName(), e.getMessage()));
            throw new RuntimeException(e);
        }

        this.jiraProjectName = config.getJiraProjectKey();
        jiraField = config.getJiraCustomFields();

        if (jiraField.containsKey(Field.ID_EXTERNAL)) {
            //Retro-compatibility external fields are historical labeled iws
            jiraField.put(Field.ID_IWS, jiraField.getOrDefault(ConfigField.ID_EXTERNAL, ""));
            jiraField.put(Field.STATUS_IWS, jiraField.getOrDefault(ConfigField.STATUS_EXTERNAL, ""));
            jiraField.put(Field.RESOLUTION_IWS, jiraField.getOrDefault(ConfigField.RESOLUTION_EXTERNAL, ""));
        }
        entStatusMapping = config.getEntStatusMapping();
        jiraAllowedTickettype = new JsonArray(config.getJiraAllowedPriority()).copy();
        this.defaultJiraTickettype = config.getDefaultTicketType();
        this.defaultPriority = config.getDefaultPriority();
        this.jiraAllowedPriority = new JsonArray(config.getJiraAllowedPriority()).copy();
    }

    /**
     * Get a modules name ENT-like, returns the module name PIVOT-like
     * Returns original name by default
     * @param moduleName ENT-like module name
     * @return PIVOT-like module name encoded in UTF-8
     */
    private String moduleEntToPivot(String moduleName) {
        return Supportpivot.applicationsMap.getOrDefault(moduleName, Field.NOTEXIST);
    }

    @Override
    public Future<PivotTicket> sendToJIRA(final PivotTicket pivotTicket) {
        Promise<PivotTicket> promise = Promise.promise();
        JiraSearch jiraSearch = new JiraSearch();

        if (StringHelper.isNullOrEmpty(pivotTicket.getIdExterne()) && StringHelper.isNullOrEmpty(pivotTicket.getIdEnt()) ) {
            LOGGER.error(String.format("[SupportPivot@%s::sendToJIRA] Error to send ticket to jira. ID_EXTERNAL or ID_ENT is mandatory %s",
                    this.getClass().getSimpleName(), pivotTicket.toJson()));
            return Future.failedFuture("2;Mandatory Field " + Field.ID_EXTERNE);
        }
        if (pivotTicket.getIdJira() != null) {
            jiraSearch.setIdJira(pivotTicket.getIdJira());
        }
        else if (pivotTicket.getIdExterne() != null) {
            jiraSearch.setIdExterne(pivotTicket.getIdExterne());
        } else {
            jiraSearch.setIdEnt(pivotTicket.getIdEnt());
        }

        ServiceManager.getInstance().getRouterService().getPivotTicket(EndpointFactory.getJiraEndpoint(), jiraSearch)
                .compose(pivotTicketResult -> {
                    if (pivotTicketResult.getIdJira() != null && !pivotTicketResult.getIdJira().isEmpty()) {
                        pivotTicket.setIdJira(pivotTicketResult.getIdJira());
                        return this.updateJiraTicket(pivotTicket);
                    } else {
                        return this.createJiraTicket(pivotTicket);
                    }
                })
                .onSuccess(promise::complete)
                .onFailure(promise::fail);

        return promise.future();
    }

    private Future<PivotTicket> createJiraTicket(PivotTicket pivotTicket) {
        Promise<PivotTicket> promise = Promise.promise();

        // Create ticket via Jira API
        HttpRequest<Buffer> request = HttpRequestHelper.getJiraAuthRequest(HttpMethod.POST, this.jiraAbsUrl.toString());

        request.sendJsonObject(prepareTicketForCreation(pivotTicket), responseAsyncResult -> {
            if (responseAsyncResult.failed()) {
                LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Sent ticket to Jira fail : %s",
                        this.getClass().getSimpleName(), prepareTicketForCreation(pivotTicket)));
                promise.fail("999;Error when creating Jira ticket");
                return;
            }
            HttpResponse<Buffer> response = responseAsyncResult.result();
            // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
            if (response.statusCode() == HttpResponseStatus.CREATED.code()) {
                JiraTicket jiraTicket = new JiraTicket(response.bodyAsJsonObject());
                pivotTicket.setIdJira(jiraTicket.getKey());
                pivotTicket.setStatutJira(Field.NOUVEAU);
                updateComments(pivotTicket.getCommentaires(), jiraTicket)
                        .compose(unused -> sendMultipleJiraPJ(pivotTicket.getIdJira(), pivotTicket.getPj()))
                        .onSuccess(event -> promise.complete(pivotTicket))
                        .onFailure(error -> {
                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error to create ticket to jira. Create comment fail %s",
                                    this.getClass().getSimpleName(), error.getMessage()));
                            promise.fail("999;Error, when creating comments.");
                        });
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Sent ticket to Jira fail : %s",
                        this.getClass().getSimpleName(), prepareTicketForCreation(pivotTicket)));
                LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error when calling URL %s : %s %s. Error when creating Jira ticket.",
                        this.getClass().getSimpleName(), this.jiraAbsUrl, response.statusCode(), response.statusMessage()));
                LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Jira error response :%s",
                        this.getClass().getSimpleName(), response.body()));
                promise.fail("999;Error when creating Jira ticket");
            }
        });

        return promise.future();
    }

    public JsonObject prepareTicketForCreation(PivotTicket pivotTicket) {

        final JsonObject jsonJiraTicket = new JsonObject();

        //changeFormatDate
        String dateCreaField = pivotTicket.getDateCreation() != null ?
                DateHelper.convertDateFormat(DateHelper.convertStringToDate(pivotTicket.getDateCreation())) : "";

        //Ticket Type
        String ticketType = pivotTicket.getTypeDemande();
        if (!jiraAllowedTickettype.contains(ticketType)) {
            ticketType = defaultJiraTickettype;
        }

        // priority PIVOT -> JIRA
        String jsonPriority = pivotTicket.getPriorite();
        if (!PIVOT_PRIORITY_LEVEL.contains(jsonPriority)) {
            jsonPriority = defaultPriority;
        }
        String currentPriority = jiraAllowedPriority.getString(PIVOT_PRIORITY_LEVEL.indexOf(jsonPriority));

        // status ent -> JIRA
        String currentStatus = pivotTicket.getStatutEnt();

        //Todo mettre le status par defaut plutot que STATUS_NEW
        String statusNameEnt = entStatusMapping.stream()
                .filter(entStatusConfig -> entStatusConfig.getName().equals(currentStatus))
                .map(StatusConfigModel::getKey)
                .findFirst()
                .orElse(Field.OUVERT);

        // reporter assistanceMLN
        String currentReporter = Field.LDE_LOWER_CASE;
        String title;
        if (!jiraField.getOrDefault(Field.ID_ENT, "").isEmpty()) {
            currentReporter = Field.ASSISTANCEMLN;
            title = String.format("[%s %s] %s", Field.ASSISTANCE_ENT, pivotTicket.getIdEnt(), pivotTicket.getTitre());
        } else {
            title = pivotTicket.getTitre();
        }

        List<String> newModules = pivotTicket.getModules().stream()
                .map(this::moduleEntToPivot)
                .collect(Collectors.toList());

        JsonObject field = new JsonObjectSafe()
                .putSafe(Field.PROJECT, new JsonObject()
                        .put(Field.KEY, jiraProjectName))
                .putSafe(Field.SUMMARY, title)
                .putSafe(Field.DESCRIPTION, pivotTicket.getDescription())
                .putSafe(Field.ISSUETYPE, new JsonObject()
                        .put(Field.NAME, ticketType))
                .putSafe(jiraField.getOrDefault(Field.ID_ENT, ""), pivotTicket.getIdEnt())
                .putSafe(jiraField.getOrDefault(Field.STATUS_ENT, ""), statusNameEnt)
                .putSafe(jiraField.getOrDefault(Field.CREATION, ""), dateCreaField)
                .putSafe(jiraField.getOrDefault(Field.RESOLUTION_ENT, ""), pivotTicket.getDateResolutionEnt())
                .putSafe(Field.REPORTER, new JsonObject().put(Field.NAME, currentReporter))
                .putSafe(jiraField.getOrDefault(Field.CREATOR, ""), pivotTicket.getDemandeur())
                .putSafe(Field.PRIORITY, new JsonObject()
                        .put(Field.NAME, currentPriority));

        if (!newModules.isEmpty() && !Field.NOTEXIST.equals(newModules.get(0))) {
            field.put(Field.COMPONENTS, new JsonArray().add(new JsonObject().put(Field.NAME, newModules.get(0))));
        }

        jsonJiraTicket.put(Field.FIELDS, field);

        return jsonJiraTicket;
    }

    private Future<Void> updateComments(List<String> newComments, JiraTicket jiraTicket) {
        Promise<Void> promise = Promise.promise();
        
        if (newComments != null && !newComments.isEmpty()) {
            Future<Void> current = Future.succeededFuture();
            for (String comment: newComments) {
                current = current.compose(unused -> sendJiraComment(jiraTicket.getId(), comment));
            }
            current.onSuccess(event -> promise.complete())
                    .onFailure(error -> {
                        LOGGER.error(String.format("[SupportPivot@%s::updateComments] Fail to send one comment %s", this.getClass().getSimpleName(), error.getMessage()));
                        promise.fail(error.getMessage());
                    });
        } else {
           promise.complete();
        }

        return promise.future();
    }

    /**
     * Send one Jira Comments
     *
     * @param idJira jira ticket id
     * @param comment comment to send
     */
    private Future<Void> sendJiraComment(final String idJira, final String comment) {
        Promise<Void> promise = Promise.promise();
        final URI urlNewTicket = this.jiraAbsUrl.resolve(idJira + "/" + Field.COMMENT);
        HttpRequest<Buffer> httpRequest = HttpRequestHelper.getJiraAuthRequest(HttpMethod.POST, urlNewTicket.toString());
        final JsonObject jsonCommTicket = new JsonObject().put(Field.BODY, comment);
        httpRequest.sendJsonObject(jsonCommTicket, responseAsyncResult -> {
            if (responseAsyncResult.failed()) {
                promise.fail("999;Error when add Jira comment : " + comment);
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraComment] POST %s", this.getClass().getSimpleName(), urlNewTicket));
                return;
            }
            HttpResponse<Buffer> response = responseAsyncResult.result();
            // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
            if (response.statusCode() != HttpResponseStatus.CREATED.code()) {
                promise.fail("999;Error when add Jira comment : " + comment + " : " + response.statusCode() + " " + response.statusMessage());
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraComment] POST %s", this.getClass().getSimpleName(), urlNewTicket));
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraComment] Error when add Jira comment on %s : %s - %s - %s",
                        this.getClass().getSimpleName(), idJira, response.statusCode(), response.statusMessage(), comment));
            } else {
                promise.complete();
            }
        });

        return promise.future();
    }

    /**
     * Send multiple PJ to JIRA
     *
     * @param jiraTicketId jira ticket id
     * @param pivotPJList list of {@link PivotPJ} list of pivotPj to send
     */
    private Future<Void> sendMultipleJiraPJ(String jiraTicketId, List<PivotPJ> pivotPJList) {
        Promise<Void> promise = Promise.promise();
        if (pivotPJList == null || pivotPJList.isEmpty()) {
            return Future.succeededFuture();
        }
        Future<Void> current = Future.succeededFuture();
        for (PivotPJ pj : pivotPJList) {
            current.compose(unused -> sendJiraPJ(jiraTicketId, pj));
        }
        current.onSuccess(event -> promise.complete())
                .onFailure(error -> {
                    LOGGER.error(String.format("[SupportPivot@%s::sendMultipleJiraPJ] Fail to send one Jira PJ %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    promise.fail(error.getMessage());
                });

        return promise.future();
    }

    /**
     * Send Jira PJ
     *
     * @param jiraTicketId jira ticket id
     * @param pj {@link PivotPJ} to send
     */
    private Future<Void> sendJiraPJ(final String jiraTicketId, final PivotPJ pj) {
        Promise<Void> promise = Promise.promise();
        final URI urlNewTicket = this.jiraAbsUrl.resolve(jiraTicketId + "/" + Field.ATTACHMENTS);
        HttpRequest<Buffer> httpRequest = HttpRequestHelper.getJiraAuthRequest(HttpMethod.POST, urlNewTicket.toString());
        String currentBoundary = generateBoundary();
        httpRequest
                .putHeader("X-Atlassian-Token", "no-check")
                .putHeader("Content-Type", "multipart/form-data; boundary=" + currentBoundary);

        String debRequest = "--" + currentBoundary + "\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" +
                pj.getNom()
                + "\"\r\n\r\n";

        String finRequest = "\r\n--" + currentBoundary + "--";

        byte[] debBytes = debRequest.getBytes();
        byte[] pjBytes = decoder.decode(pj.getContenu());
        byte[] finBytes = finRequest.getBytes();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(debBytes);
            outputStream.write(pjBytes);
            outputStream.write(finBytes);
        } catch (IOException e) {
            LOGGER.error(String.format("[SupportPivot@%s::process] Fail to write buffer %s",
                    this.getClass().getSimpleName(), e.getMessage()));
            promise.fail(e);
            return promise.future();
        }
        byte[] all = outputStream.toByteArray();

        httpRequest.putHeader("Content-Length", all.length + "");

        httpRequest.sendBuffer(Buffer.buffer(all), responseAsyncResult -> {
            if (responseAsyncResult.failed()) {
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraPJ] Error when add Jira attachment %s %s %s",
                        this.getClass().getSimpleName(), jiraTicketId, pj.getNom(), responseAsyncResult.cause()));
                promise.fail("999;Error when add Jira attachment : " + pj.getNom());
                return;
            }
            HttpResponse<Buffer> response = responseAsyncResult.result();
            if (response.statusCode() != HttpResponseStatus.OK.code()) {
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraPJ] Error when add Jira attachment %s %s code %s",
                        this.getClass().getSimpleName(), jiraTicketId, pj.getNom(), response.statusCode()));
                promise.fail("999;Error when add Jira attachment : " + pj.getNom() + " : " + response.statusCode() + " " + response.statusMessage());
            } else {
                promise.complete();
            }
        });

        return promise.future();
    }

    public Future<PivotTicket> updateJiraTicket(final PivotTicket pivotTicket) {
        Promise<PivotTicket> promise = Promise.promise();

        this.getFromJira(pivotTicket.getIdJira())
                .onSuccess(jiraTicket -> {
                    //Update Jira
                    final URI urlUpdateJiraTicket = this.jiraAbsUrl.resolve(pivotTicket.getIdJira());
                    HttpRequest<Buffer> request = HttpRequestHelper.getJiraAuthRequest(HttpMethod.PUT, urlUpdateJiraTicket.toString());

                    request.sendJsonObject(this.ticketPrepareForUpdate(pivotTicket).toJson(), modifyRespAsync -> {
                        if (modifyRespAsync.failed()) {
                            LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when calling URL %s",
                                    this.getClass().getSimpleName(), urlUpdateJiraTicket));
                            promise.fail("Error when update Jira ticket information");
                            return;
                        }
                        HttpResponse<Buffer> modifyResp = modifyRespAsync.result();
                        if (modifyResp.statusCode() == HttpResponseStatus.NO_CONTENT.code()) {

                            // Compare comments and add only new ones
                            List<String> newComments = getNewComments(pivotTicket, jiraTicket);
                            Future<Void> current = Future.succeededFuture();
                            for (String comment : newComments) {
                                String[] elem = comment.split(Pattern.quote("|"));
                                if (elem.length == COMMENT_LENGTH) {
                                    comment = " " + elem[0] + "|" + "\n" + elem[1] + "|" + "\n" + elem[2] + "|" + "\n" + "\n" + elem[3];
                                }

                                String finalComment = comment;
                                current = current.compose(unused -> sendJiraComment(jiraTicket.getId(), finalComment));
                            }
                            current.compose(event -> {
                                        // Compare PJ and add only new ones
                                        List<PivotPJ> newPJs = getNewPJs(pivotTicket, jiraTicket);
                                        return sendMultipleJiraPJ(jiraTicket.getId(), newPJs);
                                    })
                                    .onSuccess(event -> promise.complete(pivotTicket))
                                    .onFailure(error -> {
                                        LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error, when creating PJ %s",
                                                this.getClass().getSimpleName(), error.getMessage()));
                                        promise.fail("Error, when creating PJ.");
                                    });
                        } else {
                            LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when calling URL %s : %s %s",
                                    this.getClass().getSimpleName(), urlUpdateJiraTicket, modifyResp.statusMessage(), modifyResp.body().toString()));
                            promise.fail("Error when update Jira ticket information");
                        }
                    });
                })
                .onFailure(error -> {
                    LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when gathering Jira ticket information : %s",
                            this.getClass().getSimpleName(), error.getMessage()));
                    promise.fail(error);
                });
        
        return promise.future();
    }

    public JiraTicket ticketPrepareForUpdate(PivotTicket pivotTicket) {
        String title = ""; //const and find default value
        JiraTicket jiraTicket = new JiraTicket();
        JiraFields jiraFields = new JiraFields();
        Map<String, String> customFields = new HashMap<>();
        if (pivotTicket.getIdEnt() != null)
            customFields.put(jiraField.getOrDefault(Field.ID_ENT, ""), pivotTicket.getIdEnt());
        if (pivotTicket.getIdExterne() != null)
            customFields.put(jiraField.getOrDefault(Field.ID_EXTERNE, ""), pivotTicket.getIdExterne());
        if (pivotTicket.getStatutEnt() != null) {
            String currentStatus = pivotTicket.getStatutEnt();

            String statusNameEnt = entStatusMapping.stream()
                    .filter(entStatusConfig -> entStatusConfig.getName().equals(currentStatus))
                    .map(StatusConfigModel::getKey)
                    .findFirst()
                    .orElse(Field.OUVERT);
            customFields.put(jiraField.getOrDefault(Field.STATUS_ENT, ""), statusNameEnt);
        }

        if (pivotTicket.getUai() != null) {
            customFields.put(jiraField.getOrDefault(Field.UAI, ""), pivotTicket.getUai());
        }

        if (pivotTicket.getStatutExterne() != null)
            customFields.put(jiraField.getOrDefault(Field.STATUS_EXTERNE, ""), pivotTicket.getStatutExterne());
        if (pivotTicket.getDateResolutionEnt() != null)
            customFields.put(jiraField.getOrDefault(Field.RESOLUTION_ENT, ""), pivotTicket.getDateResolutionEnt());
        if (pivotTicket.getDescription() != null)
            customFields.put((Field.DESCRIPTION), pivotTicket.getDescription());
        if (!jiraField.getOrDefault(Field.ID_ENT, "").isEmpty()) {
            title = String.format("[%s %s] %s", Field.ASSISTANCE_ENT, pivotTicket.getIdEnt(), pivotTicket.getTitre());
        } else {
            title =  pivotTicket.getTitre();
        }
        if (pivotTicket.getTitre() != null)
            customFields.put(Field.SUMMARY, title);
        if (pivotTicket.getDemandeur() != null)
            customFields.put(jiraField.getOrDefault(Field.CREATOR, ""), pivotTicket.getDemandeur());

        jiraFields.setCustomFields(customFields);
        jiraTicket.setFields(jiraFields);
        return jiraTicket;
    }

    /**
     * Transform a comment from pivot format, to json
     *
     * @param comment Original full '|' separated string
     * @return JsonFormat with correct metadata (owner and date)
     */
    private JsonObject unserializeComment(String comment) {
        try {
            String[] elements = comment.split(Pattern.quote("|"));
            if (elements.length < 4) {
                return null;
            }
            JsonObject jsonComment = new JsonObject();
            jsonComment.put(Field.ID, elements[0].trim());
            jsonComment.put(Field.OWNER, elements[1].trim());
            jsonComment.put(Field.CREATED, elements[2].trim());
            StringBuilder content = new StringBuilder();
            for (int i = 3; i < elements.length; i++) {
                content.append(elements[i]);
                content.append("|");
            }
            content.deleteCharAt(content.length() - 1);
            jsonComment.put(Field.CONTENT, content.toString());
            return jsonComment;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Format date from SQL format : yyyy-MM-dd'T'HH:mm:ss
     * to pivot comment id format : yyyyMMddHHmmss
     * or display format : yyyy-MM-dd HH:mm:ss
     *
     * @param sqlDate date string to format
     * @return formatted date string
     */
    private String getDateFormatted(final String sqlDate, final boolean idStyle) {
        final DateFormat df = new SimpleDateFormat(DateHelper.SQL_FORMAT);
        Date d;
        try {
            d = df.parse(sqlDate);
        } catch (ParseException e) {
            LOGGER.error(String.format("[SupportPivot@%s::getDateFormatted] Error when parsing date %s", this.getClass().getSimpleName(), e.getMessage()));
            e.printStackTrace();
            return "iderror";
        }
        Format formatter;
        if (idStyle) {
            formatter = new SimpleDateFormat(DateHelper.yyyyMMddHHmmss);
        } else {
            formatter = new SimpleDateFormat(DateHelper.DATE_FORMAT_WITHOUT_SEPARATOR);
        }
        return formatter.format(d);
    }

    /**     
     * Get all new comment not present in jiraTicket
     * 
     * @param pivotTicket pivot ticket where the new PJ is.
     * @param jiraTicket jira ticket to see if PJ already exist
     * @return list of comment in pivot comment format
     */
    private List<String> getNewComments(PivotTicket pivotTicket, JiraTicket jiraTicket) {
        List<String> commentsToAdd = new ArrayList<>();
        for (String incomingComment : pivotTicket.getCommentaires()) {
            JsonObject issueComment = unserializeComment(incomingComment);

            if (issueComment == null || !issueComment.containsKey(Field.ID)) {
                LOGGER.error(String.format("[SupportPivot@%s::extractNewComments] Invalid comment: %s",
                        this.getClass().getSimpleName(), incomingComment));
                continue;
            }
            String issueCommentId = issueComment.getString(Field.ID, "");

            boolean existing = false;
            for (JiraComment jiraComment : jiraTicket.getFields().getComment().getComments()) {
                String ticketCommentCreated = jiraComment.getCreated().trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = jiraComment.getBody().trim();
                JsonObject ticketCommentPivotContent = unserializeComment(ticketCommentContent);
                String ticketCommentPivotId = "";
                if (ticketCommentPivotContent != null) {
                    ticketCommentPivotId = ticketCommentPivotContent.getString(Field.ID);
                }
                if (issueCommentId.equals(ticketCommentId)
                        || issueCommentId.equals(ticketCommentPivotId)) {
                    existing = true;
                    break;
                }
            }
            if (!existing) {
                commentsToAdd.add(incomingComment);
            }
        }
        return commentsToAdd;
    }

    /**
     * Get all new PJ not present in jiraTicket
     *
     * @param pivotTicket pivot ticket where the new PJ is.
     * @param jiraTicket jira ticket to see if PJ already exist
     * @return list of new {@link PivotPJ}
     */
    private List<PivotPJ> getNewPJs(PivotTicket pivotTicket, JiraTicket jiraTicket) {
        return pivotTicket.getPj().stream()
                .filter(pj -> jiraTicket.getFields().getAttachment().stream().noneMatch(jiraAttachment -> pj.getNom().equals(jiraAttachment.getFilename())))
                .collect(Collectors.toList());
    }

    /**
     * Generate a Boundary for a Multipart HTTPRequest
     * return generated Boundary
     */
    private final static char[] MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private String generateBoundary() {
        StringBuilder buffer = new StringBuilder();
        Random rand = new Random();
        int count = rand.nextInt(11) + 30; // a random size from 30 to 40
        for (int i = 0; i < count; i++) {
            buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
        }
        return buffer.toString();
    }

    @Override
    public Future<JiraTicket> getFromJira(final String jiraTicketId) {
        Promise<JiraTicket> promise = Promise.promise();

        final URI urlGetTicketGeneralInfo = this.jiraAbsUrl.resolve(jiraTicketId);

        HttpRequest<Buffer> request = HttpRequestHelper.getJiraAuthRequest(HttpMethod.GET, urlGetTicketGeneralInfo.toString());

        request.send(responseAsyncResult -> {
            if (responseAsyncResult.failed()) {
                promise.fail(String.format("Jira request error : %s", responseAsyncResult.cause()));
            } else {
                HttpResponse<Buffer> response = responseAsyncResult.result();
                if (response.statusCode() == HttpResponseStatus.OK.code()) {
                    promise.complete(new JiraTicket(response.bodyAsJsonObject()));
                } else {
                    LOGGER.error(String.format("[SupportPivot@%s::getFromJira] Error when calling URL : %s : %s %s ",
                            this.getClass().getSimpleName(), urlGetTicketGeneralInfo, response.statusCode(), response.statusMessage()));
                    LOGGER.error(response.body().toString());
                    promise.fail("Error when gathering Jira ticket information");
                }
            }
        });

        return promise.future();
    }
}