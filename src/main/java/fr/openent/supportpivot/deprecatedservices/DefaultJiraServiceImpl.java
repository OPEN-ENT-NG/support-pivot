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

package fr.openent.supportpivot.deprecatedservices;

import fr.openent.supportpivot.constants.EntConstants;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.enums.PriorityEnum;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.EitherHelper;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.status.config.JiraStatusConfig;
import fr.openent.supportpivot.services.JiraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static fr.openent.supportpivot.constants.PivotConstants.*;
import static fr.openent.supportpivot.model.ticket.PivotTicket.*;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class DefaultJiraServiceImpl implements JiraService {

    private final Logger LOGGER = LoggerFactory.getLogger(DefaultJiraServiceImpl.class);
    private final Vertx vertx;
    private final URI JIRA_HOST;
    private final String JIRA_AUTH_INFO;
    private final URI JIRA_REST_API_URI;
    private final String JIRA_PROJECT_NAME;
    private final String ACADEMY_NAME;
    private final String DEFAULT_JIRA_TICKETTYPE;
    private final String DEFAULT_PRIORITY;
    private final JsonArray JIRA_ALLOWED_PRIORITY;
    private final JsonArray JIRA_ALLOWED_TICKETTYPE;
    private final Map<String, String> JIRA_FIELD;
    private final List<JiraStatusConfig> JIRA_STATUS_MAPPING;
    private final String JIRA_STATUS_DEFAULT;

    private HttpClient httpClient;
    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static Base64.Decoder decoder = Base64.getMimeDecoder();

    private static final int HTTP_STATUS_200_OK = 200;
    private static final int HTTP_STATUS_204_NO_CONTENT = 204;
    private static final int HTTP_STATUS_404_NOT_FOUND = 404;
    private static final int HTTP_STATUS_201_CREATED = 201;

    public DefaultJiraServiceImpl(Vertx vertx) {
        ConfigModel config = ConfigManager.getInstance().getConfig();
        this.vertx = vertx;
        this.JIRA_AUTH_INFO = ConfigManager.getInstance().getJiraAuthInfo();

        this.JIRA_HOST = ConfigManager.getInstance().getJiraHostUrl();
        assert JIRA_HOST != null;

        URI jira_rest_uri = null;
        try {
            jira_rest_uri = new URI(config.getJiraURL());
        } catch (URISyntaxException e) {
            LOGGER.error(String.format("[SupportPivot@%s::DefaultJiraServiceImpl] Bad parameter ent-core.json#jira-url %s", this.getClass().getName(), e.getMessage()));
            //TODO Break module starting
        }
        this.JIRA_REST_API_URI = jira_rest_uri;
        assert JIRA_REST_API_URI != null;

        this.JIRA_PROJECT_NAME = config.getJiraProjectKey();
        this.ACADEMY_NAME = config.getAcademy();
        JIRA_FIELD = config.getJiraCustomFields();

        if (JIRA_FIELD.containsKey(JiraConstants.ID_EXTERNAL)) {
            //Retro-compatibility external fields are historical labeled iws
            JIRA_FIELD.put(Field.ID_IWS, JIRA_FIELD.getOrDefault(Field.ID_EXTERNAL, ""));
            JIRA_FIELD.put(Field.STATUS_IWS, JIRA_FIELD.getOrDefault(Field.STATUS_EXTERNAL, ""));
            JIRA_FIELD.put(Field.RESOLUTION_IWS, JIRA_FIELD.getOrDefault(Field.RESOLUTION_EXTERNAL, ""));
        }
        JIRA_STATUS_MAPPING = config.getJiraStatusMapping();
        JIRA_STATUS_DEFAULT = config.getDefaultJiraStatus().getKey();
        JIRA_ALLOWED_TICKETTYPE = new JsonArray(config.getJiraAllowedTicketType()).copy();
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
            LOGGER.error("Error when update Jira ticket", exception);
        });
        httpClientRequest.end();
    }

    /**
     * Send pivot information from IWS -- to Jira<
     * A ticket is created with the Jira API with all the json information received
     *
     * @param jsonPivotIn JSON in pivot format
     * @param handler     return JsonPivot from Jira or error message
     */
    @Deprecated
    public void sendToJIRA(final JsonObject jsonPivotIn, final Handler<Either<String, JsonObject>> handler) {
        //ID_IWS is mandatory
        if (!jsonPivotIn.containsKey(IDIWS_FIELD)
                || jsonPivotIn.getString(IDIWS_FIELD).isEmpty()) {
            handler.handle(new Either.Left<>("2;Mandatory Field " + IDIWS_FIELD));
            return;
        }

        //TITLE  is mandatory : TITLE = ID_IWS if not present
        if (!jsonPivotIn.containsKey(TITLE_FIELD) || jsonPivotIn.getString(TITLE_FIELD).isEmpty()) {
            jsonPivotIn.put(TITLE_FIELD, jsonPivotIn.getString(IDIWS_FIELD));
        }

        if (jsonPivotIn.containsKey(IDJIRA_FIELD)
                && !jsonPivotIn.getString(IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivotIn.getString(IDJIRA_FIELD);
            updateJiraTicket(jsonPivotIn, jiraTicketId, handler);
        } else {
            try {
                this.createJiraTicket(jsonPivotIn, handler);
            } catch (Error e) {
                handler.handle(new Either.Left<>("2;An error occurred  while creating ticket for id_iws " + IDIWS_FIELD + ": " + e.getMessage()));
            }
        }
    }

    private void createJiraTicket(JsonObject jsonPivotIn, Handler<Either<String, JsonObject>> handler) {
        final JsonObject jsonJiraTicket = new JsonObject();

        //Ticket Type
        String ticketType = DEFAULT_JIRA_TICKETTYPE;
        if (jsonPivotIn.containsKey(TICKETTYPE_FIELD)
                && JIRA_ALLOWED_TICKETTYPE.contains(jsonPivotIn.getString(TICKETTYPE_FIELD))) {
            ticketType = jsonPivotIn.getString(TICKETTYPE_FIELD);
        }

        // priority PIVOT -> JIRA
        String jsonPriority = jsonPivotIn.getString(PRIORITY_FIELD);
        if (!PIVOT_PRIORITY_LEVEL.contains(jsonPriority)) {
            jsonPriority = DEFAULT_PRIORITY;
        }
        String currentPriority = JIRA_ALLOWED_PRIORITY.getString(PIVOT_PRIORITY_LEVEL.indexOf(jsonPriority));

        jsonJiraTicket.put(Field.FIELDS, new JsonObject()
                .put(Field.PROJECT, new JsonObject()
                        .put(Field.KEY, JIRA_PROJECT_NAME))
                .put(Field.SUMMARY, jsonPivotIn.getString(TITLE_FIELD))
                .put(Field.DESCRIPTION, jsonPivotIn.getString(DESCRIPTION_FIELD))
                .put(Field.ISSUETYPE, new JsonObject()
                        .put(Field.NAME, ticketType))
                .put(Field.LABELS, jsonPivotIn.getJsonArray(MODULES_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.ID_ENT, ""), jsonPivotIn.getString(ID_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.ID_IWS, ""), jsonPivotIn.getString(IDIWS_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.STATUS_ENT, ""), jsonPivotIn.getString(STATUSENT_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.STATUS_IWS, ""), jsonPivotIn.getString(STATUSIWS_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.CREATION, ""), jsonPivotIn.getString(DATE_CREA_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.RESOLUTION_ENT, ""), jsonPivotIn.getString(DATE_RESO_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.RESOLUTION_IWS, ""), jsonPivotIn.getString(DATE_RESOIWS_FIELD))
                .put(JIRA_FIELD.getOrDefault(Field.CREATOR, ""), jsonPivotIn.getString(CREATOR_FIELD))
                .put(Field.PRIORITY, new JsonObject()
                        .put(Field.NAME, currentPriority)));

        try {
            // Create ticket via Jira API
            final HttpClientRequest createTicketRequest = httpClient.post(JIRA_REST_API_URI.toString(),
                    response -> {
                        // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() == HTTP_STATUS_201_CREATED) {
                            final JsonArray jsonJiraComments = jsonPivotIn.getJsonArray(COMM_FIELD);

                            updateComments(response, jsonPivotIn, jsonJiraComments,
                                    eitherCommentaires -> {
                                        if (eitherCommentaires.isRight()) {
                                            JsonObject jsonPivotCompleted = eitherCommentaires.right().getValue().getJsonObject(Field.JSONPIVOTCOMPLETED);
                                            final JsonArray jsonJiraPJ = jsonPivotIn.getJsonArray(ATTACHMENT_FIELD);
                                            updateJiraPJ(jsonPivotCompleted, jsonJiraPJ, jsonPivotIn, handler);
                                        } else {
                                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error, when creating comments %s",
                                                    this.getClass().getName(), EitherHelper.getOrNullLeftMessage(eitherCommentaires)));
                                            handler.handle(new Either.Left<>(
                                                    "999;Error, when creating comments."));
                                        }
                                    });
                        } else {
                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Sent ticket to Jira : %s", this.getClass().getName(), jsonJiraTicket));
                            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error when calling URL %s %s %s. Error when creating Jira ticket.",
                                    this.getClass().getName(), JIRA_HOST.resolve(JIRA_REST_API_URI), response.statusCode(), response.statusMessage()));
                            response.bodyHandler(event -> LOGGER.error("Jira error response :" + event.toString()));
                            handler.handle(new Either.Left<>("999;Error when creating Jira ticket"));
                        }
                    });
            createTicketRequest
                    .setChunked(true)
                    .write(jsonJiraTicket.encode());

            terminateRequest(createTicketRequest);
        } catch (Error e) {
            LOGGER.error(String.format("[SupportPivot@%s::createJiraTicket] Error when creating Jira ticket %s", this.getClass().getName(), e.getMessage()));
            handler.handle(new Either.Left<>("999;Error when creating Jira ticket: " + e.getMessage()));
        }
    }

    private void updateComments(final HttpClientResponse response, final JsonObject jsonPivot,
                                final JsonArray jsonJiraComments,
                                final Handler<Either<String, JsonObject>> handler) {
        response.bodyHandler(buffer -> {
            JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
            String idNewJiraTicket = infoNewJiraTicket.getString(Field.KEY);

            LinkedList<String> commentsLinkedList = new LinkedList<>();

            jsonPivot.put(Field.ID_JIRA, idNewJiraTicket);
            jsonPivot.put(Field.STATUT_JIRA, Field.NOUVEAU);

            if (jsonJiraComments != null) {
                for (Object comment : jsonJiraComments) {
                    commentsLinkedList.add(comment.toString());
                }
                sendJiraComments(idNewJiraTicket, commentsLinkedList, jsonPivot, handler);
            } else {
                handler.handle(new Either.Right<>(new JsonObject()
                        .put(Field.STATUS, Field.OK)
                        .put(Field.JSONPIVOTCOMPLETED, jsonPivot)
                ));
            }
        });
    }

    /**
     * Send Jira Comments
     *
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList, final JsonObject jsonPivot,
                                  final Handler<Either<String, JsonObject>> handler) {
        if (commentsLinkedList.size() > 0) {
            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira + "/comment");

            final HttpClientRequest commentTicketRequest = httpClient.post(urlNewTicket.toString(), response -> {

                // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                if (response.statusCode() != HTTP_STATUS_201_CREATED) {
                    LOGGER.error(String.format("[SupportPivot@%s::sendJiraComments] POST %s",
                            this.getClass().getName(), JIRA_HOST.resolve(urlNewTicket)));
                    LOGGER.error(String.format("[SupportPivot@%s::sendJiraComments] Error when add Jira comment on %s : %s - %s - %s",
                            this.getClass().getName(), idJira, response.statusCode(), response.statusMessage(), commentsLinkedList.getFirst().toString()));
                    handler.handle(new Either.Left<>("999;Error when add Jira comment : " + commentsLinkedList.getFirst().toString() + " : " + response.statusCode() + " " + response.statusMessage()));
                    return;
                }
                //Recursive call
                commentsLinkedList.removeFirst();
                sendJiraComments(idJira, commentsLinkedList, jsonPivot, handler);
            });
            commentTicketRequest.setChunked(true);

            final JsonObject jsonCommTicket = new JsonObject();
            jsonCommTicket.put(Field.BODY, commentsLinkedList.getFirst().toString());
            commentTicketRequest.write(jsonCommTicket.encode());

            terminateRequest(commentTicketRequest);

        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put(Field.STATUS, Field.OK)
                    .put(Field.JSONPIVOTCOMPLETED, jsonPivot)
            ));
        }
    }

    /**
     * Send PJ from IWS to JIRA
     *
     * @param jsonPivotCompleted jsonJiraPJ jsonPivot handler
     */
    private void updateJiraPJ(final JsonObject jsonPivotCompleted,
                              final JsonArray jsonJiraPJ,
                              final JsonObject jsonPivot,
                              final Handler<Either<String, JsonObject>> handler) {

        String idJira = jsonPivotCompleted.getString(IDJIRA_FIELD);

        LinkedList<JsonObject> pjLinkedList = new LinkedList<>();

        if (jsonJiraPJ != null && jsonJiraPJ.size() > 0) {
            for (Object o : jsonJiraPJ) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject pj = (JsonObject) o;
                pjLinkedList.add(pj);
            }
            sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);
        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put(Field.STATUS, Field.OK)
                    .put(Field.JSONPIVOTCOMPLETED, jsonPivot)
            ));
        }
    }

    /**
     * Send Jira PJ
     *
     * @param idJira, pjLinkedList, jsonPivot, jsonPivotCompleted, handler
     */
    private void sendJiraPJ(final String idJira,
                            final LinkedList<JsonObject> pjLinkedList,
                            final JsonObject jsonPivotCompleted,
                            final Handler<Either<String, JsonObject>> handler) {
        if (pjLinkedList.size() > 0) {
            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira + "/attachments");

            final HttpClientRequest postAttachmentsRequest = httpClient.post(urlNewTicket.toString(), response -> {

                if (response.statusCode() != HTTP_STATUS_200_OK) {
                    LOGGER.error(String.format("[SupportPivot@%s::sendJiraPJ] Error when add Jira attachment %s %s",
                            this.getClass().getName(), idJira, pjLinkedList.getFirst().getString(Field.NOM, "")));
                    handler.handle(new Either.Left<>("999;Error when add Jira attachment : " + pjLinkedList.getFirst().getString("nom") + " : " + response.statusCode() + " " + response.statusMessage()));
                    return;
                }
                //Recursive call
                pjLinkedList.removeFirst();
                sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);

            });

            String currentBoundary = generateBoundary();

            postAttachmentsRequest.putHeader("X-Atlassian-Token", "no-check")
                    .putHeader("Content-Type", "multipart/form-data; boundary=" + currentBoundary);

            String debRequest = "--" + currentBoundary + "\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" +
                    pjLinkedList.getFirst().getString(Field.NOM)
                    + "\"\r\n\r\n";

            String finRequest = "\r\n--" + currentBoundary + "--";

            byte[] debBytes = debRequest.getBytes();
            byte[] pjBytes = decoder.decode(pjLinkedList.getFirst().getString(Field.CONTENU));
            byte[] finBytes = finRequest.getBytes();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(debBytes);
                outputStream.write(pjBytes);
                outputStream.write(finBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] all = outputStream.toByteArray();

            postAttachmentsRequest.putHeader("Content-Length", all.length + "")
                    .write(Buffer.buffer(all));

            terminateRequest(postAttachmentsRequest);
        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put(Field.STATUS, Field.OK)
                    .put(Field.JSONPIVOTCOMPLETED, jsonPivotCompleted)
            ));
        }

    }

    public void updateJiraTicket(final JsonObject jsonPivotIn, final String jiraTicketId,
                                 final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId);

        final HttpClientRequest getTicketInfosRequest = httpClient.get(urlGetTicketGeneralInfo.toString(),
                response -> {

                    switch (response.statusCode()) {
                        case (HTTP_STATUS_200_OK):
                            response.bodyHandler(bufferGetInfosTicket -> {
                                JsonObject jsonCurrentTicketInfos = new JsonObject(bufferGetInfosTicket.toString());
                                //Is JIRA ticket had been created by IWS ?
                                String jiraTicketIdIWS = jsonCurrentTicketInfos.getJsonObject(Field.FIELDS).getString(JIRA_FIELD.getOrDefault(Field.ID_IWS, ""));
                                if (jiraTicketIdIWS == null) {
                                    LOGGER.error(String.format("[SupportPivot@%s::checkMissingFields] Not an IWS ticket.",
                                            this.getClass().getName()));
                                    handler.handle(new Either.Left<>("102;Not an IWS ticket."));
                                    return;
                                }

                                //Is JIRA ticket had been created by same IWS issue ?
                                String jsonPivotIdIWS = jsonPivotIn.getString(IDIWS_FIELD);
                                if (!jiraTicketIdIWS.equals(jsonPivotIdIWS)) {
                                    //todo delete
                                    handler.handle(new Either.Left<>("102;JIRA Ticket " + jiraTicketId + " already link with an another IWS issue"));
                                    return;
                                }

                                //Convert jsonPivotIn into jsonJiraTicket
                                final JsonObject jsonJiraUpdateTicket = new JsonObject();
                                jsonJiraUpdateTicket.put(Field.FIELDS, new JsonObject()
                                        .put(JIRA_FIELD.getOrDefault(Field.STATUS_ENT, ""), jsonPivotIn.getString(STATUSENT_FIELD))
                                        .put(JIRA_FIELD.getOrDefault(Field.STATUS_IWS, ""), jsonPivotIn.getString(STATUSIWS_FIELD))
                                        .put(JIRA_FIELD.getOrDefault(Field.RESOLUTION_ENT, ""), jsonPivotIn.getString(DATE_RESO_FIELD))
                                        .put(JIRA_FIELD.getOrDefault(Field.RESOLUTION_IWS, ""), jsonPivotIn.getString(DATE_RESOIWS_FIELD))
                                        .put(Field.DESCRIPTION, jsonPivotIn.getString(DESCRIPTION_FIELD))
                                        .put(Field.SUMMARY, jsonPivotIn.getString(TITLE_FIELD))
                                        .put(JIRA_FIELD.getOrDefault(Field.CREATOR, ""), jsonPivotIn.getString(CREATOR_FIELD)));

                                //Update Jira
                                final URI urlUpdateJiraTicket = JIRA_REST_API_URI.resolve(jiraTicketId);
                                final HttpClientRequest modifyTicketRequest = httpClient.put(urlUpdateJiraTicket.toString(), modifyResp -> {
                                    if (modifyResp.statusCode() == HTTP_STATUS_204_NO_CONTENT) {

                                        // Compare comments and add only new ones
                                        JsonArray jsonPivotTicketComments = jsonPivotIn.getJsonArray(Field.COMMENTAIRES, new JsonArray());
                                        JsonArray jsonCurrentTicketComments = jsonCurrentTicketInfos.getJsonObject(Field.FIELDS).getJsonObject(Field.COMMENT).getJsonArray(Field.COMMENTS);
                                        JsonArray newComments = extractNewComments(jsonCurrentTicketComments, jsonPivotTicketComments);

                                        LinkedList<String> commentsLinkedList = new LinkedList<>();

                                        if (newComments != null) {
                                            for (Object comment : newComments) {
                                                commentsLinkedList.add(comment.toString());
                                            }
                                            sendJiraComments(jiraTicketId, commentsLinkedList, jsonPivotIn, eitherCommentaires -> {
                                                if (eitherCommentaires.isRight()) {
                                                    JsonObject jsonPivotCompleted = eitherCommentaires.right().getValue().getJsonObject(Field.JSONPIVOTCOMPLETED);

                                                    // Compare PJ and add only new ones
                                                    JsonArray jsonPivotTicketPJ = jsonPivotIn.getJsonArray(Field.PJ, new JsonArray());
                                                    JsonArray jsonCurrentTicketPJ = jsonCurrentTicketInfos.getJsonObject(Field.FIELDS).getJsonArray(Field.ATTACHMENT);
                                                    JsonArray newPJs = extractNewPJs(jsonCurrentTicketPJ, jsonPivotTicketPJ);
                                                    updateJiraPJ(jsonPivotCompleted, newPJs, jsonPivotIn, handler);

                                                } else {
                                                    LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error, when creating PJ. %s",
                                                            this.getClass().getName(), EitherHelper.getOrNullLeftMessage(eitherCommentaires)));
                                                    handler.handle(new Either.Left<>("Error, when creating PJ."));
                                                }
                                            });
                                        } else {
                                            handler.handle(new Either.Right<>(new JsonObject().put(Field.STATUS, Field.OK)));
                                        }
                                    } else {
                                        LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when calling URL: %s",
                                                this.getClass().getName(), modifyResp.statusMessage()));
                                        handler.handle(new Either.Left<>("Error when update Jira ticket information"));
                                    }
                                });

                                modifyTicketRequest.setChunked(true)
                                        .write(jsonJiraUpdateTicket.encode());

                                terminateRequest(modifyTicketRequest);
                            });
                            break;
                        case (HTTP_STATUS_404_NOT_FOUND):
                            LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Unknown JIRA Ticket: %s",
                                    this.getClass().getName(), jiraTicketId));
                            handler.handle(new Either.Left<>("101;Unknown JIRA Ticket " + jiraTicketId));
                            break;
                        default:
                            LOGGER.error(String.format("[SupportPivot@%s::updateJiraTicket] Error when calling URL: %s",
                                    this.getClass().getName(), response.statusMessage()));
                            response.bodyHandler(event -> LOGGER.error("Jira response : " + event));
                            handler.handle(new Either.Left<>("999;Error when getting Jira ticket information"));
                    }
                }
        );
        terminateRequest(getTicketInfosRequest);
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
        //df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d;
        try {
            d = df.parse(sqlDate);
        } catch (ParseException e) {
            LOGGER.error("Support : error when parsing date");
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
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     *
     * @param inJiraComments   comments of Jira ticket
     * @param incomingComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewComments(JsonArray inJiraComments, JsonArray incomingComments) {
        JsonArray commentsToAdd = new fr.wseduc.webutils.collections.JsonArray();
        for (Object incomingComment : incomingComments) {
            if (!(incomingComment instanceof String)) continue;
            String rawComment = (String) incomingComment;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if (issueComment != null && issueComment.containsKey(Field.ID)) {
                issueCommentId = issueComment.getString(Field.ID, "");
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::extractNewComments] Invalid comment : %s",
                        this.getClass().getName(), rawComment));
                continue;
            }

            boolean existing = false;
            for (Object jiraComment : inJiraComments) {
                if (!(jiraComment instanceof JsonObject)) continue;
                JsonObject ticketComment = (JsonObject) jiraComment;
                String ticketCommentCreated = ticketComment.getString(Field.CREATED, "").trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = ticketComment.getString(Field.BODY, "").trim();
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
                commentsToAdd.add(rawComment);
            }
        }
        return commentsToAdd;
    }

    /**
     * Compare PJ.
     * Add every comment to ticket not already existing
     *
     * @param inJiraPJs   PJ of Jira ticket
     * @param incomingPJs PJ of pivot issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewPJs(JsonArray inJiraPJs, JsonArray incomingPJs) {
        JsonArray pjToAdd = new fr.wseduc.webutils.collections.JsonArray();

        for (Object oi : incomingPJs) {
            if (!(oi instanceof JsonObject)) continue;
            JsonObject pjIssuePivot = (JsonObject) oi;
            String issuePivotName;

            if (pjIssuePivot.containsKey(Field.NOM)) {
                issuePivotName = pjIssuePivot.getString(Field.NOM, "");
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::extractNewComments] Invalid PJ : %s",
                        this.getClass().getName(), pjIssuePivot));
                continue;
            }

            boolean existing = false;
            for (Object ot : inJiraPJs) {
                if (!(ot instanceof JsonObject)) continue;
                JsonObject pjTicketJiraPJ = (JsonObject) ot;
                String ticketPJName = pjTicketJiraPJ.getString(Field.FILENAME, "").trim();

                if (issuePivotName.equals(ticketPJName)) {
                    existing = true;
                    break;
                }
            }
            if (!existing) {
                pjToAdd.add(pjIssuePivot);
            }
        }
        return pjToAdd;
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

    /**
     * Get Jira ticket infos
     *
     * @param jiraTicketId Jira ticket ID
     */
    public void getFromJira(final HttpServerRequest request, final String jiraTicketId,
                            final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId);

        HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo.toString(), response -> {
            response.exceptionHandler(exception ->
                    LOGGER.error(String.format("[SupportPivot@%s::getFromJira] Jira request error: %s", this.getClass().getName(), exception)));
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    convertJiraReponseToJsonPivot(jsonGetInfosTicket, handler);
                });
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::getFromJira] Error when calling URL: %s %s %s",
                        this.getClass().getName(), JIRA_HOST.resolve(urlGetTicketGeneralInfo), response.statusCode(), response.statusMessage()));
                response.bodyHandler(bufferGetInfosTicket -> LOGGER.error(bufferGetInfosTicket.toString()));
                handler.handle(new Either.Left<>("Error when gathering Jira ticket information"));
            }
        });

        terminateRequest(httpClientRequestGetInfo);
    }

    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    public void convertJiraReponseToJsonPivot(final JsonObject jiraTicket,
                                              final Handler<Either<String, JsonObject>> handler) {
        JsonObject fields = jiraTicket.getJsonObject(Field.FIELDS);

        if (!fields.containsKey(JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, ""))
                || fields.getString(JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, "")) == null) {
            String message = String.format("[SupportPivot@%s::sendJiraTicketToSupport] Supportpivot Field %s does not exist",
                    this.getClass().getSimpleName(), JIRA_FIELD.getOrDefault(EntConstants.IDENT_FIELD, ""));
            handler.handle(new Either.Left<>(message));
        } else {
            final JsonObject jsonPivot = new JsonObject();

            jsonPivot.put(IDJIRA_FIELD, jiraTicket.getString(ID));

            jsonPivot.put(COLLECTIVITY_FIELD, ConfigManager.getInstance().getConfig().getCollectivity());
            jsonPivot.put(ACADEMY_FIELD, ACADEMY_NAME);

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.CREATOR, "")) != null) {
                jsonPivot.put(CREATOR_FIELD,
                        fields.getString(stringEncode(JIRA_FIELD.getOrDefault(Field.CREATOR, ""))));
            } else {
                jsonPivot.put(CREATOR_FIELD, "");
            }

            jsonPivot.put(TICKETTYPE_FIELD, fields
                    .getJsonObject(Field.ISSUETYPE).getString(Field.NAME));
            jsonPivot.put(TITLE_FIELD, fields.getString(Field.SUMMARY));

            if (fields.getString(Field.DESCRIPTION) != null) {
                jsonPivot.put(DESCRIPTION_FIELD,
                        fields.getString(Field.DESCRIPTION));
            } else {
                jsonPivot.put(DESCRIPTION_FIELD, "");
            }

            String currentPriority = fields.getJsonObject(Field.PRIORITY).getString(Field.NAME);
            currentPriority = PriorityEnum.getValue(currentPriority).getPivotName();

            jsonPivot.put(PRIORITY_FIELD, currentPriority);

            jsonPivot.put(MODULES_FIELD, fields.getJsonArray(Field.LABELS));

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.ID_ENT, "")) != null) {
                jsonPivot.put(ID_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.ID_ENT, "")));
            } else {
                jsonPivot.put(ID_FIELD, "");
            }

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.ID_IWS, "")) != null) {
                jsonPivot.put(IDIWS_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.ID_IWS, "")));
            }

            JsonArray comm = fields.getJsonObject(Field.COMMENT)
                    .getJsonArray(Field.COMMENTS, new fr.wseduc.webutils.collections.JsonArray());
            JsonArray jsonCommentArray = new fr.wseduc.webutils.collections.JsonArray();
            for (int i = 0; i < comm.size(); i++) {
                JsonObject comment = comm.getJsonObject(i);
                //Write only if the comment is public
                if (!comment.containsKey(Field.VISIBILITY)) {
                    String commentFormated = serializeComment(comment);
                    jsonCommentArray.add(commentFormated);
                }
            }
            jsonPivot.put(COMM_FIELD, jsonCommentArray);

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.STATUS_ENT, "")) != null) {
                jsonPivot.put(STATUSENT_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.STATUS_ENT, "")));
            }
            if (fields.getString(JIRA_FIELD.getOrDefault(Field.STATUS_IWS, "")) != null) {
                jsonPivot.put(STATUSIWS_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.STATUS_IWS, "")));
            }

            String currentStatus = fields.getJsonObject(Field.STATUS).getString(Field.NAME);

            String currentStatusToIWS = JIRA_STATUS_MAPPING.stream()
                    .filter(jiraStatusConfig -> jiraStatusConfig.contains(currentStatus))
                    .map(JiraStatusConfig::getKey)
                    .findFirst()
                    .orElse(JIRA_STATUS_DEFAULT);

            jsonPivot.put(STATUSJIRA_FIELD, currentStatusToIWS);

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.CREATION, "")) != null) {
                jsonPivot.put(DATE_CREA_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.CREATION, "")));
            }

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.RESOLUTION_IWS, "")) != null) {
                jsonPivot.put(DATE_RESOIWS_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.RESOLUTION_IWS, "")));
            }

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.RESOLUTION_ENT, "")) != null) {
                jsonPivot.put(DATE_RESO_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.RESOLUTION_ENT, "")));
            }

            if (fields.getString(Field.RESOLUTIONDATE) != null) {
                String dateFormated = getDateFormatted(fields.getString(Field.RESOLUTIONDATE), false);
                jsonPivot.put(DATE_RESOJIRA_FIELD, dateFormated);
            }

            if (fields.getString(JIRA_FIELD.getOrDefault(Field.RESPONSE_TECHNICAL, "")) != null) {
                jsonPivot.put(TECHNICAL_RESP_FIELD,
                        fields.getString(JIRA_FIELD.getOrDefault(Field.RESPONSE_TECHNICAL, "")));
            }

            jsonPivot.put(ATTRIBUTION_FIELD, ATTRIBUTION_IWS);

            JsonArray attachments = fields.getJsonArray(Field.ATTACHMENT, new fr.wseduc.webutils.collections.JsonArray());

            final JsonArray allPJConverted = new fr.wseduc.webutils.collections.JsonArray();
            final AtomicInteger nbAttachment = new AtomicInteger(attachments.size());
            final AtomicBoolean responseSent = new AtomicBoolean(false);

            for (Object attachment : attachments) {
                if (!(attachment instanceof JsonObject)) {
                    nbAttachment.decrementAndGet();
                    continue;
                }

                final JsonObject attachmentInfos = (JsonObject) attachment;
                getJiraPJ(attachmentInfos, getJiraPjResp -> {
                            if (getJiraPjResp.isRight()) {
                                String b64FilePJ = getJiraPjResp.right().getValue().getString(Field.B64ATTACHMENT);
                                JsonObject currentPJ = new JsonObject();
                                currentPJ.put(ATTACHMENT_NAME_FIELD, attachmentInfos.getString(Field.FILENAME));
                                currentPJ.put(ATTACHMENT_CONTENT_FIELD, b64FilePJ);
                                allPJConverted.add(currentPJ);
                            } else {
                                handler.handle(getJiraPjResp);
                            }

                            //last attachment handles the response
                            if (nbAttachment.decrementAndGet() <= 0) {
                                jsonPivot.put(ATTACHMENT_FIELD, allPJConverted);
                                responseSent.set(true);
                                handler.handle(new Either.Right<>(jsonPivot));
                            }
                        }
                );
            }

            //if no attachment handle the response
            if (!responseSent.get() && nbAttachment.get() == 0) {
                handler.handle(new Either.Right<>(jsonPivot));
            }
        }
    }

    /**
     * Get Jira PJ via Jira API
     */
    private void getJiraPJ(final JsonObject attachmentInfos,
                           final Handler<Either<String, JsonObject>> handler) {
        String attachmentLink = attachmentInfos.getString(Field.CONTENT);

        final HttpClientRequest getAttachmentrequest = httpClient.get(attachmentLink, response -> {
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    String b64Attachment = encoder.encodeToString(bufferGetInfosTicket.getBytes());
                    handler.handle(new Either.Right<>(
                            new JsonObject().put(Field.STATUS, Field.OK)
                                    .put(Field.B64ATTACHMENT, b64Attachment)));
                });
            } else {
                LOGGER.error(String.format("[SupportPivot@%s::process] Error when calling URL : %s %s",
                        this.getClass().getName(), attachmentLink, response.statusMessage()));
                handler.handle(new Either.Left<>("Error when getting Jira attachment (" + attachmentLink + ") information"));
            }
        });

        terminateRequest(getAttachmentrequest);
    }

    /**
     * Serialize comments : date | author | content
     *
     * @param comment Json Object with a comment to serialize
     * @return String with comment serialized
     */
    private String serializeComment(final JsonObject comment) {
        String content = getDateFormatted(comment.getString(Field.CREATED), true)
                + " | " + comment.getJsonObject(Field.AUTHOR).getString(Field.DISPLAYNAME)
                + " | " + getDateFormatted(comment.getString(Field.CREATED), false)
                + " | " + comment.getString(Field.BODY);

        String origContent = comment.getString(Field.BODY);

        return hasToSerialize(origContent) ? content : origContent;
    }

    /**
     * Check if comment must be serialized
     * If it's '|' separated (at least 4 fields)
     * And first field is 14 number (AAAMMJJHHmmSS)
     * Then it must not be serialized
     *
     * @param content Comment to check
     * @return true if the comment has to be serialized
     */
    private boolean hasToSerialize(String content) {
        String[] elements = content.split(Pattern.quote("|"));
        return elements.length != 4;
    }

    /**
     * Encode a string in UTF-8
     *
     * @param in String to encode
     * @return encoded String
     */
    private String stringEncode(String in) {
        return new String(in.getBytes(), StandardCharsets.UTF_8);
    }

}