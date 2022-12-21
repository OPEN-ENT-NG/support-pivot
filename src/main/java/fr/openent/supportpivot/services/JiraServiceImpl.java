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

package fr.openent.supportpivot.services;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.ConfigField;
import fr.openent.supportpivot.constants.EntConstants;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.jira.JiraComment;
import fr.openent.supportpivot.model.jira.JiraTicket;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.model.status.config.EntStatusConfig;
import fr.openent.supportpivot.model.status.config.StatusConfigModel;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;

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

import static fr.openent.supportpivot.constants.PivotConstants.*;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class JiraServiceImpl implements JiraService {

    private final Logger LOGGER = LoggerFactory.getLogger(JiraServiceImpl.class);
    private final Vertx vertx;
    private final URI JIRA_HOST;
    private final String JIRA_AUTH_INFO;
    private final URI JIRA_REST_API_URI;
    private final String JIRA_PROJECT_NAME;
    private final String DEFAULT_JIRA_TICKETTYPE;
    private final String DEFAULT_PRIORITY;
    private final JsonArray JIRA_ALLOWED_PRIORITY;
    private final JsonArray JIRA_ALLOWED_TICKETTYPE;
    private final Map<String, String> JIRA_FIELD;
    private final List<EntStatusConfig> ENT_STATUS_MAPPING;

    private HttpClient httpClient;
    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static Base64.Decoder decoder = Base64.getMimeDecoder();

    private static final int HTTP_STATUS_200_OK = 200;
    private static final int HTTP_STATUS_204_NO_CONTENT = 204;
    private static final int HTTP_STATUS_404_NOT_FOUND = 404;
    private static final int HTTP_STATUS_201_CREATED = 201;

    public JiraServiceImpl(Vertx vertx) {
        ConfigModel config = ConfigManager.getInstance().getConfig();

        this.vertx = vertx;
        this.JIRA_AUTH_INFO = ConfigManager.getInstance().getJiraAuthInfo();

        this.JIRA_HOST = ConfigManager.getInstance().getJiraHostUrl();
        assert JIRA_HOST != null;

        URI jira_rest_uri = null;
        try {
            jira_rest_uri = new URI(config.getJiraURL());
        } catch (URISyntaxException e) {
            LOGGER.error(String.format("[SupportPivot@%s::JiraServiceImpl] Bad parameter ent-core.json#jira-url %s", this.getClass().getSimpleName(), e.getMessage()));
            //TODO Break module starting
        }
        this.JIRA_REST_API_URI = jira_rest_uri;
        assert JIRA_REST_API_URI != null;

        this.JIRA_PROJECT_NAME = config.getJiraProjectKey();
        JIRA_FIELD = config.getJiraCustomFields();

        if (JIRA_FIELD.containsKey(JiraConstants.ID_EXTERNAL)) {
            //Retro-compatibility external fields are historical labeled iws
            JIRA_FIELD.put(Field.ID_IWS, JIRA_FIELD.getOrDefault(ConfigField.ID_EXTERNAL, ""));
            JIRA_FIELD.put(Field.STATUS_IWS, JIRA_FIELD.getOrDefault(ConfigField.STATUS_EXTERNAL, ""));
            JIRA_FIELD.put(Field.RESOLUTION_IWS, JIRA_FIELD.getOrDefault(ConfigField.RESOLUTION_EXTERNAL, ""));
        }
        ENT_STATUS_MAPPING = config.getEntStatusMapping();
        JIRA_ALLOWED_TICKETTYPE = new JsonArray(config.getJiraAllowedPriority()).copy();
        this.DEFAULT_JIRA_TICKETTYPE = config.getDefaultTicketType();
        this.DEFAULT_PRIORITY = config.getDefaultPriority();
        this.JIRA_ALLOWED_PRIORITY = new JsonArray(config.getJiraAllowedPriority()).copy();

        this.httpClient = generateHttpClient(JIRA_HOST);
    }

    /**
     * Generate HTTP client
     *
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() : (Field.HTTPS.equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl(Field.HTTPS.equals(uri.getScheme()))
                .setKeepAlive(true);

        if (ConfigManager.getInstance().getConfig().getProxyHost() != null) {
            ProxyOptions proxy = new ProxyOptions();
            proxy.setHost(ConfigManager.getInstance().getConfig().getProxyHost());
            proxy.setPort(ConfigManager.getInstance().getConfig().getProxyPort());
            httpClientOptions.setProxyOptions(proxy);
        }

        return vertx.createHttpClient(httpClientOptions);
    }

    private void terminateRequest(HttpClientRequest httpClientRequest) {
        httpClientRequest.putHeader("Authorization", "Basic " + encoder.encodeToString(JIRA_AUTH_INFO.getBytes()))
                .setFollowRedirects(true);
        if (!httpClientRequest.headers().contains("Content-Type")) {
            httpClientRequest.putHeader("Content-Type", "application/json");
        }

        httpClientRequest.exceptionHandler(exception -> {
            LOGGER.error(String.format("[SupportPivot@%s::terminateRequest] Error when update Jira ticket %s", this.getClass().getSimpleName(), exception.getMessage()));
        });
        httpClientRequest.end();
    }

    /**
     * Get a modules name ENT-like, returns the module name PIVOT-like
     * Returns original name by default
     * @param moduleName ENT-like module name
     * @return PIVOT-like module name encoded in UTF-8
     */
    private String moduleEntToPivot(String moduleName) {
        return Supportpivot.applicationsMap.getOrDefault(moduleName, JiraConstants.NOTEXIST);
    }

    @Override
    public Future<PivotTicket> sendToJIRA(final PivotTicket pivotTicket) {
        //ID_EXTERNAL is mandatory
        if (pivotTicket.getIdExterne() == null || pivotTicket.getIdExterne().isEmpty()) {
            LOGGER.error(String.format("[SupportPivot@%s::sendToJIRA] Error to send ticket to jira. ID_EXTERNAL is mandatory %s",
                    this.getClass().getSimpleName(), pivotTicket.toJson()));
            return Future.failedFuture("2;Mandatory Field " + Field.ID_EXTERNE);
        }

        if (pivotTicket.getIdJira() != null && !pivotTicket.getIdJira().isEmpty()) {
            return this.updateJiraTicket(pivotTicket);
        } else {
            return this.createJiraTicket(pivotTicket);
        }
    }

    private Future<PivotTicket> createJiraTicket(PivotTicket pivotTicket) {
        Promise<PivotTicket> promise = Promise.promise();
        try {
            // Create ticket via Jira API
            final HttpClientRequest createTicketRequest = httpClient.post(JIRA_REST_API_URI.toString(),
                    response -> {
                        // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() == HTTP_STATUS_201_CREATED) {
                            response.bodyHandler(body -> {
                                JiraTicket jiraTicket = new JiraTicket(new JsonObject(body));
                                pivotTicket.setIdJira(jiraTicket.getId());
                                pivotTicket.setStatutJira(Field.NOUVEAU);
                                updateComments(pivotTicket.getCommentaires(), jiraTicket)
                                        .compose(unused -> sendMultipleJiraPJ(pivotTicket.getIdJira(), pivotTicket.getPj()))
                                        .onSuccess(event -> promise.complete(pivotTicket))
                                        .onFailure(error -> {
                                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error to create ticket to jira. Create comment fail %s",
                                                    this.getClass().getSimpleName(), error.getMessage()));
                                            promise.fail("999;Error, when creating comments.");
                                        });
                            });
                        } else {
                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Sent ticket to Jira : %s",
                                    this.getClass().getSimpleName(), prepareTicketForCreation(pivotTicket)));
                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error when calling URL %s : %s %s. Error when creating Jira ticket.",
                                    this.getClass().getSimpleName(), JIRA_HOST.resolve(JIRA_REST_API_URI), response.statusCode(), response.statusMessage()));
                            response.bodyHandler(event -> LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Jira error response :%s",
                                    this.getClass().getSimpleName(), event.toString())));
                            promise.fail("999;Error when creating Jira ticket");
                        }
                    });
            createTicketRequest
                    .setChunked(true)
                    .write(prepareTicketForCreation(pivotTicket).encode());

            terminateRequest(createTicketRequest);
        } catch (Error e) {
            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error when creating Jira ticket %s",
                    this.getClass().getSimpleName(), e.getMessage()));
            promise.fail("999;Error when creating Jira ticket: " + e.getMessage());
        }

        return promise.future();
    }

    public JsonObject prepareTicketForCreation(PivotTicket pivotTicket) {

        final JsonObject jsonJiraTicket = new JsonObject();

        //changeFormatDate
        Date date = DateHelper.convertStringToDate(pivotTicket.getDateCreation());
        String dateCreaField = DateHelper.convertDateFormat(date);

        //Ticket Type
        String ticketType = pivotTicket.getTypeDemande();
        if (!JIRA_ALLOWED_TICKETTYPE.contains(ticketType)) {
            ticketType = DEFAULT_JIRA_TICKETTYPE;
        }

        // priority PIVOT -> JIRA
        String jsonPriority = pivotTicket.getPriorite();
        if (!PIVOT_PRIORITY_LEVEL.contains(jsonPriority)) {
            jsonPriority = DEFAULT_PRIORITY;
        }
        String currentPriority = JIRA_ALLOWED_PRIORITY.getString(PIVOT_PRIORITY_LEVEL.indexOf(jsonPriority));

        // status ent -> JIRA
        String currentStatus = pivotTicket.getStatutEnt();

        //Todo mettre le status par defaut plutot que STATUS_NEW
        String statusNameEnt = ENT_STATUS_MAPPING.stream()
                .filter(entStatusConfig -> entStatusConfig.getName().equals(currentStatus))
                .map(StatusConfigModel::getKey)
                .findFirst()
                .orElse(STATUS_NEW);

        // reporter assistanceMLN
        String currentReporter = JiraConstants.REPORTER_LDE;
        String title;
        if (!JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, "").isEmpty()) {
            currentReporter = JiraConstants.REPORTER_ENT;
            title = String.format("[%s %s] %s", ASSISTANCE_ENT, pivotTicket.getIdEnt(), pivotTicket.getTitre());
        } else {
            title = pivotTicket.getTitre();
        }

        List<String> newModules = pivotTicket.getModule().stream()
                .map(this::moduleEntToPivot)
                .collect(Collectors.toList());

        JsonObject field = new JsonObject()
                .put(JiraConstants.PROJECT, new JsonObject()
                        .put(JiraConstants.PROJECT_KEY, JIRA_PROJECT_NAME))
                .put(JiraConstants.TITLE_FIELD, title)
                .put(JiraConstants.DESCRIPTION_FIELD, pivotTicket.getDescription())
                .put(JiraConstants.ISSUETYPE, new JsonObject()
                        .put(NAME, ticketType))
                .put(JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, ""), pivotTicket.getIdEnt())
                .put(JIRA_FIELD.getOrDefault(EntConstants.STATUSENT_FIELD, ""), statusNameEnt)
                .put(JIRA_FIELD.getOrDefault(EntConstants.CREATION_FIELD, ""), dateCreaField)
                .put(JIRA_FIELD.getOrDefault(EntConstants.RESOLUTION_ENT, ""), pivotTicket.getDateResolutionEnt())
                .put(JiraConstants.REPORTER, new JsonObject().put(NAME, currentReporter))
                .put(JIRA_FIELD.getOrDefault(EntConstants.CREATOR, ""), pivotTicket.getDemandeur())
                .put(JiraConstants.PRIORITY, new JsonObject()
                        .put(NAME, currentPriority));

        if (!newModules.isEmpty() && !JiraConstants.NOTEXIST.equals(newModules.get(0))) {
            field.put(JiraConstants.COMPONENTS, new JsonArray().add(new JsonObject().put(JiraConstants.NAME, newModules.get(0))));
        }

        jsonJiraTicket.put(JiraConstants.FIELDS, field);

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
        final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira + "/" + Field.COMMENT);
        final HttpClientRequest commentTicketRequest = httpClient.post(urlNewTicket.toString(), response -> {

            // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
            if (response.statusCode() != HTTP_STATUS_201_CREATED) {
                promise.fail("999;Error when add Jira comment : " + comment + " : " + response.statusCode() + " " + response.statusMessage());
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraComment] POST %s", this.getClass().getSimpleName(), JIRA_HOST.resolve(urlNewTicket)));
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraComment] Error when add Jira comment on %s : %s - %s - %s",
                        this.getClass().getSimpleName(), idJira, response.statusCode(), response.statusMessage(), comment));
            } else {
                promise.complete();
            }
        });
        commentTicketRequest.setChunked(true);

        final JsonObject jsonCommTicket = new JsonObject();
        jsonCommTicket.put(Field.BODY, comment);
        commentTicketRequest.write(jsonCommTicket.encode());

        terminateRequest(commentTicketRequest);
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
        final URI urlNewTicket = JIRA_REST_API_URI.resolve(jiraTicketId + "/" + Field.ATTACHMENTS);
        final HttpClientRequest postAttachmentsRequest = httpClient.post(urlNewTicket.toString(), response -> {

            if (response.statusCode() != HTTP_STATUS_200_OK) {
                promise.fail("999;Error when add Jira attachment : " + pj.getNom() + " : " + response.statusCode() + " " + response.statusMessage());
                LOGGER.error(String.format("[SupportPivot@%s::sendJiraPJ] Error when add Jira attachment %s %s",
                        this.getClass().getSimpleName(), jiraTicketId, pj.getNom()));
            } else {
                promise.complete();
            }
        });

        String currentBoundary = generateBoundary();

        postAttachmentsRequest.putHeader("X-Atlassian-Token", "no-check")
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

        postAttachmentsRequest.putHeader("Content-Length", all.length + "")
                .write(Buffer.buffer(all));

        terminateRequest(postAttachmentsRequest);

        return promise.future();
    }

    public Future<PivotTicket> updateJiraTicket(final PivotTicket pivotTicket) {
        Promise<PivotTicket> promise = Promise.promise();
        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(pivotTicket.getIdJira());

        final HttpClientRequest getTicketInfosRequest = httpClient.get(urlGetTicketGeneralInfo.toString(),
                response -> {

                    switch (response.statusCode()) {
                        case (HTTP_STATUS_200_OK):
                            response.bodyHandler(bufferGetInfosTicket -> {
                                JiraTicket jiraTicket = new JiraTicket(new JsonObject(bufferGetInfosTicket.toString()));
                                //Convert jsonPivotIn into jsonJiraTicket

                                //Update Jira
                                final URI urlUpdateJiraTicket = JIRA_REST_API_URI.resolve(pivotTicket.getIdJira());
                                final HttpClientRequest modifyTicketRequest = httpClient.put(urlUpdateJiraTicket.toString(), modifyResp -> {
                                    if (modifyResp.statusCode() == HTTP_STATUS_204_NO_CONTENT) {

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
                                        LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when calling URL %s : %s",
                                                this.getClass().getSimpleName(), urlUpdateJiraTicket, modifyResp.statusMessage()));
                                        modifyResp.bodyHandler(body -> LOGGER.error(body.toString()));
                                        promise.fail("Error when update Jira ticket information");
                                    }
                                });

                                modifyTicketRequest.setChunked(true)
                                        .write(this.ticketPrepareForUpdate(pivotTicket).encode());

                                terminateRequest(modifyTicketRequest);
                            });
                            break;
                        case (HTTP_STATUS_404_NOT_FOUND):
                            promise.fail("101;Unknown JIRA Ticket " + pivotTicket.getIdJira());
                            break;
                        default:
                            LOGGER.error("Error when calling URL : " + response.statusMessage());
                            response.bodyHandler(event -> LOGGER.error("Jira response : " + event));
                            promise.fail("999;Error when getting Jira ticket information");
                    }
                }
        );
        terminateRequest(getTicketInfosRequest);
        
        return promise.future();
    }

    //todo return JiraTicket/new model JiraTicketUpdate ?
    public JsonObject ticketPrepareForUpdate(PivotTicket pivotTicket) {
        String title = "";//const and find default value
        final JsonObject jsonJiraUpdateTicket = new JsonObject();
        JsonObject fields = new JsonObject();
        if (pivotTicket.getIdEnt() != null)
            fields.put(JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, ""), pivotTicket.getIdEnt());
        if (pivotTicket.getIdExterne() != null)
            fields.put(JIRA_FIELD.getOrDefault(Field.ID_EXTERNE, ""), pivotTicket.getIdExterne());
        if (pivotTicket.getStatutEnt() != null) {
            String currentStatus = pivotTicket.getStatutEnt();

            String statusNameEnt = ENT_STATUS_MAPPING.stream()
                    .filter(entStatusConfig -> entStatusConfig.getName().equals(currentStatus))
                    .map(StatusConfigModel::getKey)
                    .findFirst()
                    .orElse(EntConstants.STATUS_NAME_ENT);
            fields.put(JIRA_FIELD.getOrDefault(EntConstants.STATUSENT_FIELD, ""), statusNameEnt);
        }

        if (pivotTicket.getStatutExterne() != null)
            fields.put(JIRA_FIELD.getOrDefault(EntConstants.STATUSEXTERNAL_FIELD, ""), pivotTicket.getStatutExterne());
        if (pivotTicket.getDateResolutionEnt() != null)
            fields.put(JIRA_FIELD.getOrDefault(EntConstants.RESOLUTION_ENT, ""), pivotTicket.getDateResolutionEnt());
        if (pivotTicket.getDescription() != null)
            fields.put((EntConstants.DESCRIPTION_FIELD), pivotTicket.getDescription());
        if (!JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, "").isEmpty()) {
            title = String.format("[%s %s] %s", ASSISTANCE_ENT, pivotTicket.getIdEnt(), pivotTicket.getTitre());
        } else {
            title =  pivotTicket.getTitre();
        }
        if (pivotTicket.getTitre() != null)
            fields.put(JiraConstants.TITLE_FIELD, title);
        if (pivotTicket.getDemandeur() != null)
            fields.put(JIRA_FIELD.getOrDefault(EntConstants.CREATOR, ""), pivotTicket.getDemandeur());
        jsonJiraUpdateTicket.put(JiraConstants.FIELDS, fields);

        return jsonJiraUpdateTicket;
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
    public void getFromJira(final HttpServerRequest request, final String jiraTicketId,
                            final Handler<Either<String, JsonObject>> handler) {
        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId);

        HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo.toString(), response -> {
            response.exceptionHandler(exception -> LOGGER.error("Jira request error : ", exception));
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    handler.handle(new Either.Right<>(jsonGetInfosTicket));
                });
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::getFromJira] Error when calling URL : %s : %s %s ",
                        this.getClass().getSimpleName(), JIRA_HOST.resolve(urlGetTicketGeneralInfo), response.statusCode(), response.statusMessage()));
                response.bodyHandler(bufferGetInfosTicket -> LOGGER.error(bufferGetInfosTicket.toString()));
                handler.handle(new Either.Left<>("Error when gathering Jira ticket information"));
            }
        });

        terminateRequest(httpClientRequestGetInfo);
    }
}