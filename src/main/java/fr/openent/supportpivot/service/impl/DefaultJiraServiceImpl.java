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

package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static io.vertx.core.buffer.Buffer.buffer;

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
    private final String COLLECTIVITY_NAME;
    private final String ACADEMY_NAME;
    private final String DEFAULT_JIRA_TICKETTYPE;
    private final String DEFAULT_PRIORITY;
    private final JsonArray JIRA_ALLOWED_PRIORITY;
    private final JsonArray JIRA_ALLOWED_TICKETTYPE;
    private final JsonObject JIRA_FIELD;
    private final JsonObject JIRA_STATUS_MAPPING;
    private final String JIRA_STATUS_DEFAULT;

    private HttpClient httpClient;
    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static Base64.Decoder decoder = Base64.getMimeDecoder();

    private static final int HTTP_STATUS_200_OK = 200;
    private static final int HTTP_STATUS_204_NO_CONTENT = 204;
    private static final int HTTP_STATUS_404_NOT_FOUND= 404;
    private static final int HTTP_STATUS_201_CREATED = 201;

    DefaultJiraServiceImpl(Vertx vertx, JsonObject config) {

        this.vertx = vertx;
        String jiraLogin = config.getString("jira-login");
        String jiraPassword = config.getString("jira-passwd");
        this.JIRA_AUTH_INFO = jiraLogin + ":" + jiraPassword;

        URI jira_host_uri = null;
        try {
            jira_host_uri = new URI(config.getString("jira-host"));
        } catch (URISyntaxException e) {
            LOGGER.error("Bad parameter ent-core.json#jira-host ", e);
            //TODO Break module starting
        }
        this.JIRA_HOST = jira_host_uri;

        URI jira_rest_uri = null;
        try {
            jira_rest_uri = new URI(config.getString("jira-url"));
        } catch (URISyntaxException e) {
            LOGGER.error("Bad parameter ent-core.json#jira-url ", e);
            //TODO Break module starting
        }
        this.JIRA_REST_API_URI = jira_rest_uri;

        this.JIRA_PROJECT_NAME = config.getString("jira-project-key");
        this.COLLECTIVITY_NAME = config.getString("collectivity");
        this.ACADEMY_NAME = config.getString("academy");
        JIRA_FIELD = config.getJsonObject("jira-custom-fields");
        JIRA_STATUS_MAPPING = config.getJsonObject("jira-status-mapping").getJsonObject("statutsJira");
        JIRA_STATUS_DEFAULT = config.getJsonObject("jira-status-mapping").getString("statutsDefault");
        JIRA_ALLOWED_TICKETTYPE = config.getJsonArray("jira-allowed-tickettype");
        this.DEFAULT_JIRA_TICKETTYPE = config.getString("default-tickettype");
        this.DEFAULT_PRIORITY = config.getString("default-priority");
        this.JIRA_ALLOWED_PRIORITY = config.getJsonArray("jira-allowed-priority");

        this.httpClient = generateHttpClient(JIRA_HOST);
    }

    /**
     * Generate HTTP client
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ?  443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl("https".equals(uri.getScheme()))
                .setKeepAlive(true);
        return vertx.createHttpClient(httpClientOptions);
    }

    private void addHeaders(final RequestOptions options) {
        options.putHeader("Authorization", "Basic " + encoder.encodeToString(JIRA_AUTH_INFO.getBytes()))
                .setFollowRedirects(true);
        if (!options.getHeaders().contains("Content-Type")) {
            options.putHeader("Content-Type", "application/json");
        }
    }
    /**
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivotIn JSON in pivot format
     * @param handler return JsonPivot from Jira or error message
     */
    public void sendToJIRA(final JsonObject jsonPivotIn, final Handler<Either<String, JsonObject>> handler) {

        //ID_IWS is mandatory
        if (!jsonPivotIn.containsKey(Supportpivot.IDIWS_FIELD)
                || jsonPivotIn.getString(Supportpivot.IDIWS_FIELD).isEmpty()) {

            handler.handle(new Either.Left<>("2;Mandatory Field " + Supportpivot.IDIWS_FIELD));
            return;
        }

        //TITLE  is mandatory : TITLE = ID_IWS if not present
        if (!jsonPivotIn.containsKey(Supportpivot.TITLE_FIELD) || jsonPivotIn.getString(Supportpivot.TITLE_FIELD).isEmpty() ){
            jsonPivotIn.put(Supportpivot.TITLE_FIELD, jsonPivotIn.getString(Supportpivot.IDIWS_FIELD));
        }

        if (jsonPivotIn.containsKey(Supportpivot.IDJIRA_FIELD)
            && !jsonPivotIn.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivotIn.getString(Supportpivot.IDJIRA_FIELD);
            updateJiraTicket(jsonPivotIn, jiraTicketId, handler);
        } else {
            createJiraTicket(jsonPivotIn, handler);

        }
    }

    private void createJiraTicket(JsonObject jsonPivotIn, Handler<Either<String, JsonObject>> handler) {
        final JsonObject jsonJiraTicket = new JsonObject();

        //Ticket Type
        String ticketType = DEFAULT_JIRA_TICKETTYPE;
        if (jsonPivotIn.containsKey(Supportpivot.TICKETTYPE_FIELD)
                && JIRA_ALLOWED_TICKETTYPE.contains(jsonPivotIn.getString(Supportpivot.TICKETTYPE_FIELD))) {
            ticketType = jsonPivotIn.getString(Supportpivot.TICKETTYPE_FIELD);
        }

        // priority PIVOT -> JIRA
        String jsonPriority = jsonPivotIn.getString(Supportpivot.PRIORITY_FIELD);
        if(!Supportpivot.PIVOT_PRIORITY_LEVEL.contains(jsonPriority)){
            jsonPriority = DEFAULT_PRIORITY;
        }
        String currentPriority = JIRA_ALLOWED_PRIORITY.getString(Supportpivot.PIVOT_PRIORITY_LEVEL.indexOf(jsonPriority));

        jsonJiraTicket.put("fields", new JsonObject()
                .put("project", new JsonObject()
                        .put("key", JIRA_PROJECT_NAME))
                .put("summary", jsonPivotIn.getString(Supportpivot.TITLE_FIELD))
                .put("description", jsonPivotIn.getString(Supportpivot.DESCRIPTION_FIELD))
                .put("issuetype", new JsonObject()
                        .put("name", ticketType))
                .put("labels", jsonPivotIn.getJsonArray(Supportpivot.MODULES_FIELD))
                .put(JIRA_FIELD.getString("id_ent"), jsonPivotIn.getString(Supportpivot.IDENT_FIELD))
                .put(JIRA_FIELD.getString("id_iws"), jsonPivotIn.getString(Supportpivot.IDIWS_FIELD))
                .put(JIRA_FIELD.getString("status_ent"), jsonPivotIn.getString(Supportpivot.STATUSENT_FIELD))
                .put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(Supportpivot.STATUSIWS_FIELD))
                .put(JIRA_FIELD.getString("creation"), jsonPivotIn.getString(Supportpivot.DATE_CREA_FIELD))
                .put(JIRA_FIELD.getString("resolution_ent"), jsonPivotIn.getString(Supportpivot.DATE_RESOENT_FIELD))
                .put(JIRA_FIELD.getString("resolution_iws"), jsonPivotIn.getString(Supportpivot.DATE_RESOIWS_FIELD))
                .put(JIRA_FIELD.getString("creator"), jsonPivotIn.getString(Supportpivot.CREATOR_FIELD))
                .put("priority", new JsonObject()
                        .put("name", currentPriority)));

        // Create ticket via Jira API
        final RequestOptions requestOptions = new RequestOptions()
          .setMethod(HttpMethod.GET)
          .setURI(JIRA_REST_API_URI.toString());
        addHeaders(requestOptions);
        httpClient.request(requestOptions)
          .map(r -> r.setChunked(true))
          .flatMap(r -> r.send(jsonJiraTicket.encode()))
          .onSuccess(response -> {
            // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
            if (response.statusCode() == HTTP_STATUS_201_CREATED) {
                final JsonArray jsonJiraComments = jsonPivotIn.getJsonArray(Supportpivot.COMM_FIELD);

                updateComments(response, jsonPivotIn, jsonJiraComments,
                        EitherCommentaires -> {
                            if (EitherCommentaires.isRight()) {
                                JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");
                                final JsonArray jsonJiraPJ = jsonPivotIn.getJsonArray(Supportpivot.ATTACHMENT_FIELD);
                                updateJiraPJ(jsonPivotCompleted, jsonJiraPJ, jsonPivotIn, handler);
                            } else {
                                handler.handle(new Either.Left<>(
                                        "999;Error, when creating comments."));
                            }
                        });
            } else {
                LOGGER.error("Sent ticket to Jira : " + jsonJiraTicket);
                LOGGER.error("Error when calling URL " + JIRA_HOST.resolve(JIRA_REST_API_URI) + " : " + response.statusCode() + response.statusMessage() + ". Error when creating Jira ticket.");
                response.bodyHandler(event -> LOGGER.error("Jira error response :" + event.toString()));
                handler.handle(new Either.Left<>("999;Error when creating Jira ticket"));
            }
        })
      .onFailure(t -> {
          LOGGER.warn("Error while creating JIRA ticket", t);
          handler.handle(new Either.Left<>("999;Error when creating Jira ticket"));
      });
    }

    private void updateComments(final HttpClientResponse response, final JsonObject jsonPivot,
                                 final JsonArray jsonJiraComments,
                                 final Handler<Either<String, JsonObject>> handler) {



            response.bodyHandler(buffer -> {

                JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
                String idNewJiraTicket = infoNewJiraTicket.getString("key");

                LinkedList<String> commentsLinkedList = new LinkedList<>();

                jsonPivot.put("id_jira", idNewJiraTicket);
                jsonPivot.put("statut_jira", "Nouveau");

                if ( jsonJiraComments != null ) {
                    for( Object comment : jsonJiraComments ) {
                        commentsLinkedList.add(comment.toString());
                    }
                    sendJiraComments(idNewJiraTicket, commentsLinkedList, jsonPivot, handler);
                }
                else {
                    handler.handle(new Either.Right<>(new JsonObject()
                            .put("status", "OK")
                            .put("jsonPivotCompleted", jsonPivot)
                    ));
                }

            });


    }


    /**
     * Send Jira Comments
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList, final JsonObject jsonPivot,
                                  final Handler<Either<String, JsonObject>> handler) {
        if( commentsLinkedList.size() > 0 ) {
            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira+"/comment");
            final RequestOptions requestOptions = new RequestOptions()
              .setMethod(HttpMethod.POST)
              .setURI(urlNewTicket.toString());
            addHeaders(requestOptions);
            httpClient.request(requestOptions)
              .map(r -> r.setChunked(true))
              .flatMap(r -> {
                  final JsonObject jsonCommTicket = new JsonObject();
                  jsonCommTicket.put("body", commentsLinkedList.getFirst().toString());
                  return r.send(jsonCommTicket.encode());
              })
              .onSuccess(response -> {
                // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                if (response.statusCode() != HTTP_STATUS_201_CREATED) {
                    handler.handle(new Either.Left<>("999;Error when add Jira comment : " + commentsLinkedList.getFirst().toString() + " : " + response.statusCode() + " " + response.statusMessage()));
                    LOGGER.error("POST " + JIRA_HOST.resolve(urlNewTicket));
                    LOGGER.error("Error when add Jira comment on " + idJira + " : " + response.statusCode() + " - "+ response.statusMessage() + " - "+ commentsLinkedList.getFirst().toString());
                    return;
                }
                //Recursive call
                commentsLinkedList.removeFirst();
                sendJiraComments(idJira, commentsLinkedList, jsonPivot, handler);
              })
              .onFailure(t -> {
                  LOGGER.error("Error when add Jira comment on " + idJira, t);
                  handler.handle(new Either.Left<>("999;Error when add Jira comment : " + commentsLinkedList.getFirst().toString() + " : " + t.getMessage()));
              });

        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
            ));
        }
    }



    /**
     * Send PJ from IWS to JIRA
     * @param jsonPivotCompleted jsonJiraPJ jsonPivot handler
     */
    private void updateJiraPJ(final JsonObject jsonPivotCompleted,
                            final JsonArray jsonJiraPJ,
                            final JsonObject jsonPivot,
                            final Handler<Either<String, JsonObject>> handler) {

        String idJira = jsonPivotCompleted.getString(Supportpivot.IDJIRA_FIELD);

        LinkedList<JsonObject> pjLinkedList = new LinkedList<>();

        if ( jsonJiraPJ != null && jsonJiraPJ.size() > 0 ) {
            for( Object o : jsonJiraPJ ) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject pj = (JsonObject) o;
                pjLinkedList.add(pj);
            }
            sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);
        }
        else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
            ));
        }

    }


    /**
     * Send Jira PJ
     * @param idJira, pjLinkedList, jsonPivot, jsonPivotCompleted, handler
     */
    private void sendJiraPJ(final String idJira,
                                final LinkedList<JsonObject> pjLinkedList,
                                final JsonObject jsonPivotCompleted,
                                final Handler<Either<String, JsonObject>> handler) {
        if( pjLinkedList.size() > 0 ) {
            String currentBoundary = generateBoundary();
            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira+"/attachments");
            final RequestOptions options = new RequestOptions()
              .setMethod(HttpMethod.POST)
              .setURI(urlNewTicket.toString())
              .addHeader("X-Atlassian-Token", "no-check")
              .putHeader("Content-Type", "multipart/form-data; boundary=" + currentBoundary);
            final String debRequest = "--" + currentBoundary + "\r\n" +
              "Content-Type: application/octet-stream\r\n" +
              "Content-Disposition: form-data; name=\"file\"; filename=\"" +
              pjLinkedList.getFirst().getString("nom")
              + "\"\r\n\r\n";
            final String finRequest = "\r\n--" + currentBoundary + "--";

            byte[] debBytes = debRequest.getBytes();
            byte[] pjBytes = decoder.decode(pjLinkedList.getFirst().getString("contenu"));
            byte[] finBytes = finRequest.getBytes();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(debBytes);
                outputStream.write(pjBytes);
                outputStream.write(finBytes);
            } catch (IOException e) {
                LOGGER.error("An error occurred while writing data for JIRA ticket to send", e);
            }
            final byte[] all = outputStream.toByteArray();
            options.addHeader("Content-Length", all.length + "");
            httpClient.request(options)
            .flatMap(r -> r.send(buffer(all)))
            .onSuccess(response -> {
                if (response.statusCode() != HTTP_STATUS_200_OK) {
                    handler.handle(new Either.Left<>("999;Error when add Jira attachment : " + pjLinkedList.getFirst().getString("nom") + " : " + response.statusCode() + " " + response.statusMessage()));
                    LOGGER.error("Error when add Jira attachment" + idJira +  pjLinkedList.getFirst().getString("nom"));
                    return;
                }
                //Recursive call
                pjLinkedList.removeFirst();
                sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);

            })
            .onFailure(t -> {
                LOGGER.error("Error when add Jira attachment" + idJira +  pjLinkedList.getFirst().getString("nom"), t);
                handler.handle(new Either.Left<>("999;Error when add Jira attachment : " + pjLinkedList.getFirst().getString("nom") + " : " + t.getMessage()));
            });
        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivotCompleted)
            ));
        }

    }

    private void updateJiraTicket(final JsonObject jsonPivotIn, final String jiraTicketId,
                                  final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId) ;
        final RequestOptions options = new RequestOptions()
          .setMethod(HttpMethod.GET)
          .setURI(urlGetTicketGeneralInfo.toString());
        addHeaders(options);
        httpClient.request(options)
         .flatMap(HttpClientRequest::send)
          .onFailure(t -> {
              LOGGER.error("Error when calling " + options.getURI(), t);
              handler.handle(new Either.Left<>("999;Error when updating Jira ticket information"));
          })
          .onSuccess(response -> {
                switch (response.statusCode()){
                    case (HTTP_STATUS_200_OK) :
                        response.bodyHandler(bufferGetInfosTicket -> {
                            JsonObject jsonCurrentTicketInfos = new JsonObject(bufferGetInfosTicket.toString());
                            //Is JIRA ticket had been created by IWS ?
                            String jiraTicketIdIWS =jsonCurrentTicketInfos.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws"));
                            if(jiraTicketIdIWS==null){
                                handler.handle(new Either.Left<>("102;Not an IWS ticket."));
                                return;
                            }

                            //Is JIRA ticket had been created by same IWS issue ?
                            String jsonPivotIdIWS = jsonPivotIn.getString(Supportpivot.IDIWS_FIELD);
                            if(!jiraTicketIdIWS.equals(jsonPivotIdIWS)){
                                handler.handle(new Either.Left<>("102;JIRA Ticket " + jiraTicketId + " already link with an another IWS issue"));
                                return;
                            }

                            //Convert jsonPivotIn into jsonJiraTicket
                            final JsonObject jsonJiraUpdateTicket = new JsonObject();
                            jsonJiraUpdateTicket.put("fields", new JsonObject()
                                    .put(JIRA_FIELD.getString("status_ent"), jsonPivotIn.getString(Supportpivot.STATUSENT_FIELD))
                                    .put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(Supportpivot.STATUSIWS_FIELD))
                                    .put(JIRA_FIELD.getString("resolution_ent"), jsonPivotIn.getString(Supportpivot.DATE_RESOENT_FIELD))
                                    .put(JIRA_FIELD.getString("resolution_iws"), jsonPivotIn.getString(Supportpivot.DATE_RESOIWS_FIELD))
                                    .put(("description"), jsonPivotIn.getString(Supportpivot.DESCRIPTION_FIELD))
                                    .put("summary", jsonPivotIn.getString(Supportpivot.TITLE_FIELD))
                                    .put(JIRA_FIELD.getString("creator"), jsonPivotIn.getString(Supportpivot.CREATOR_FIELD)));

                            //Update Jira
                            final URI urlUpdateJiraTicket = JIRA_REST_API_URI.resolve(jiraTicketId);
                            final RequestOptions updateOptions = new RequestOptions()
                              .setMethod(HttpMethod.PUT)
                              .setURI(urlUpdateJiraTicket.toString());
                            addHeaders(updateOptions);
                            httpClient.request(updateOptions)
                              .map(r -> r.setChunked(true))
                              .flatMap(r -> r.send(jsonJiraUpdateTicket.encode()))
                              .onSuccess(modifyResp -> {
                                if (modifyResp.statusCode() == HTTP_STATUS_204_NO_CONTENT) {

                                    // Compare comments and add only new ones
                                    JsonArray jsonPivotTicketComments = jsonPivotIn.getJsonArray("commentaires", new JsonArray());
                                    JsonArray jsonCurrentTicketComments = jsonCurrentTicketInfos.getJsonObject("fields").getJsonObject("comment").getJsonArray("comments");
                                    JsonArray newComments = extractNewComments(jsonCurrentTicketComments, jsonPivotTicketComments);

                                    LinkedList<String> commentsLinkedList = new LinkedList<>();

                                    if ( newComments != null ) {
                                        for( Object comment : newComments ) {
                                            commentsLinkedList.add(comment.toString());
                                        }
                                        sendJiraComments(jiraTicketId, commentsLinkedList, jsonPivotIn, EitherCommentaires -> {
                                            if (EitherCommentaires.isRight()) {
                                                JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");

                                                // Compare PJ and add only new ones
                                                JsonArray jsonPivotTicketPJ = jsonPivotIn.getJsonArray("pj", new JsonArray());
                                                JsonArray jsonCurrentTicketPJ = jsonCurrentTicketInfos.getJsonObject("fields").getJsonArray("attachment");
                                                JsonArray newPJs = extractNewPJs(jsonCurrentTicketPJ, jsonPivotTicketPJ);
                                                updateJiraPJ(jsonPivotCompleted, newPJs, jsonPivotIn, handler);

                                            } else {
                                                handler.handle(new Either.Left<>(
                                                        "Error, when creating PJ."));
                                            }
                                        });
                                    }
                                    else {
                                        handler.handle(new Either.Right<>(new JsonObject().put("status", "OK")));
                                    }
                                }
                                else {
                                    LOGGER.error("Error when calling URL : " + modifyResp.statusMessage());
                                    handler.handle(new Either.Left<>("Error when update Jira ticket information"));
                                }
                            })
                              .onFailure(t -> {
                                  LOGGER.error("An error occurred while trying to update Jira ticket " + jiraTicketId, t);
                                  handler.handle(new Either.Left<>("101;Unknown JIRA Ticket update error : " + t.getMessage()));
                              });

                        });
                        break;
                    case (HTTP_STATUS_404_NOT_FOUND) :
                        handler.handle(new Either.Left<>("101;Unknown JIRA Ticket " + jiraTicketId));
                        break;
                    default :
                        LOGGER.error("Error when calling URL : " + response.statusMessage());
                        response.bodyHandler(event -> LOGGER.error("Jira response : " + event));
                        handler.handle(new Either.Left<>("999;Error when getting Jira ticket information"));
                }
            }
        );
    }


    /**
     * Transform a comment from pivot format, to json
     * @param comment Original full '|' separated string
     * @return JsonFormat with correct metadata (owner and date)
     */
    private JsonObject unserializeComment(String comment) {
        try{
            String[] elements = comment.split(Pattern.quote("|"));
            if(elements.length < 4) {
                return null;
            }
            JsonObject jsonComment = new JsonObject();
            jsonComment.put("id", elements[0].trim());
            jsonComment.put("owner", elements[1].trim());
            jsonComment.put("created", elements[2].trim());
            StringBuilder content = new StringBuilder();
            for(int i = 3; i<elements.length ; i++) {
                content.append(elements[i]);
                content.append("|");
            }
            content.deleteCharAt(content.length() - 1);
            jsonComment.put("content", content.toString());
            return jsonComment;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Format date from SQL format : yyyy-MM-dd'T'HH:mm:ss
     * to pivot comment id format : yyyyMMddHHmmss
     * or display format : yyyy-MM-dd HH:mm:ss
     * @param sqlDate date string to format
     * @return formatted date string
     */
    private String getDateFormatted (final String sqlDate, final boolean idStyle) {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
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
        if(idStyle) {
            formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        } else {
            formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        }
        return formatter.format(d);
    }

    /**
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     * @param inJiraComments comments of Jira ticket
     * @param incomingComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewComments(JsonArray inJiraComments, JsonArray incomingComments) {
        JsonArray commentsToAdd = new fr.wseduc.webutils.collections.JsonArray();
        for(Object incomingComment : incomingComments)  {
            if( !(incomingComment instanceof String) ) continue;
            String rawComment = (String)incomingComment;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if(issueComment != null && issueComment.containsKey("id")) {
                issueCommentId = issueComment.getString("id", "");
            } else {
                LOGGER.error("Support : Invalid comment : " + rawComment);
                continue;
            }

            boolean existing = false;
            for(Object jiraComment : inJiraComments) {
                if (!(jiraComment instanceof JsonObject)) continue;
                JsonObject ticketComment = (JsonObject) jiraComment;
                String ticketCommentCreated = ticketComment.getString("created", "").trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = ticketComment.getString("body", "").trim();
                JsonObject ticketCommentPivotContent = unserializeComment(ticketCommentContent);
                String ticketCommentPivotId = "";
                if( ticketCommentPivotContent != null ) {
                    ticketCommentPivotId = ticketCommentPivotContent.getString("id");
                }
                if(issueCommentId.equals(ticketCommentId)
                        || issueCommentId.equals(ticketCommentPivotId)) {
                    existing = true;
                    break;
                }
            }
            if(!existing) {
                commentsToAdd.add(rawComment);
            }
        }
        return commentsToAdd;
    }


    /**
     * Compare PJ.
     * Add every comment to ticket not already existing
     * @param inJiraPJs PJ of Jira ticket
     * @param incomingPJs PJ of pivot issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewPJs(JsonArray inJiraPJs, JsonArray incomingPJs) {
        JsonArray pjToAdd = new fr.wseduc.webutils.collections.JsonArray();

        for(Object oi : incomingPJs)  {
            if( !(oi instanceof JsonObject) ) continue;
            JsonObject pjIssuePivot = (JsonObject) oi;
            String issuePivotName;


            if(pjIssuePivot.containsKey("nom")) {
                issuePivotName = pjIssuePivot.getString("nom", "");
            } else {
                LOGGER.error("Support : Invalid PJ : " + pjIssuePivot);
                continue;
            }

            boolean existing = false;

            for(Object ot : inJiraPJs) {
                if (!(ot instanceof JsonObject)) continue;
                JsonObject pjTicketJiraPJ = (JsonObject) ot;
                String ticketPJName = pjTicketJiraPJ.getString("filename", "").trim();

                if(issuePivotName.equals(ticketPJName)) {
                    existing = true;
                    break;
                }
            }
            if(!existing) {
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
     * @param jiraTicketId Jira ticket ID
     */
    public void getFromJira(final HttpServerRequest request, final String jiraTicketId,
                            final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId) ;
        final RequestOptions options = new RequestOptions()
          .setMethod(HttpMethod.GET)
          .setURI(urlGetTicketGeneralInfo.toString());
        addHeaders(options);
        httpClient.request(options)
        .flatMap(HttpClientRequest::send)
        .onSuccess(response -> {
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    convertJiraReponseToJsonPivot(jsonGetInfosTicket, handler);
                });
            } else {
                LOGGER.error("Error when calling URL : " +JIRA_HOST.resolve(urlGetTicketGeneralInfo) + ":" + response.statusMessage());
                handler.handle(new Either.Left<>("Error when gathering Jira ticket information"));
            }
        })
        .onFailure(t -> {
            LOGGER.error("Error when calling URL : " +JIRA_HOST.resolve(urlGetTicketGeneralInfo), t);
            handler.handle(new Either.Left<>("Error when gathering Jira ticket information"));
        });

    }


    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    private void convertJiraReponseToJsonPivot(final JsonObject jiraTicket,
                                               final Handler<Either<String, JsonObject>> handler) {

        JsonObject fields = jiraTicket.getJsonObject("fields");

        if  (!fields.containsKey(JIRA_FIELD.getString("id_iws"))
            || fields.getString(JIRA_FIELD.getString("id_iws")) == null) {
                handler.handle(new Either.Left<>("Field " + JIRA_FIELD.getString("id_iws") + " does not exist."));
        } else {

            final JsonObject jsonPivot = new JsonObject();

            jsonPivot.put(Supportpivot.IDJIRA_FIELD, jiraTicket.getString("key"));

            jsonPivot.put(Supportpivot.COLLECTIVITY_FIELD, COLLECTIVITY_NAME);
            jsonPivot.put(Supportpivot.ACADEMY_FIELD, ACADEMY_NAME);

            if (fields.getString(JIRA_FIELD.getString("creator")) != null) {
                jsonPivot.put(Supportpivot.CREATOR_FIELD,
                        fields.getString(stringEncode(JIRA_FIELD.getString("creator"))));
            } else {
                jsonPivot.put(Supportpivot.CREATOR_FIELD, "");
            }

            jsonPivot.put(Supportpivot.TICKETTYPE_FIELD, fields
                    .getJsonObject("issuetype").getString("name"));
            jsonPivot.put(Supportpivot.TITLE_FIELD, fields.getString("summary"));

            if (fields.getString("description") != null) {
                jsonPivot.put(Supportpivot.DESCRIPTION_FIELD,
                        fields.getString("description"));
            } else {
                jsonPivot.put(Supportpivot.DESCRIPTION_FIELD, "");
            }


            String currentPriority = fields.getJsonObject("priority").getString("name");
            switch (currentPriority) {
                case "Lowest":
                case "Mineure":
                    currentPriority = "Mineur";
                    break;
                case "High":
                case "Majeure":
                    currentPriority = "Majeur";
                    break;
                case "Highest":
                case "Bloquante":
                    currentPriority = "Bloquant";
                    break;
                default:
                    currentPriority = "Mineur";
                    break;
            }

            jsonPivot.put(Supportpivot.PRIORITY_FIELD, currentPriority);

            jsonPivot.put(Supportpivot.MODULES_FIELD, fields.getJsonArray("labels"));

            if (fields.getString(JIRA_FIELD.getString("id_ent")) != null) {
                jsonPivot.put(Supportpivot.IDENT_FIELD,
                        fields.getString(JIRA_FIELD.getString("id_ent")));
            } else {
                jsonPivot.put(Supportpivot.IDENT_FIELD, "");
            }

            if (fields.getString(JIRA_FIELD.getString("id_iws")) != null) {
                jsonPivot.put(Supportpivot.IDIWS_FIELD,
                        fields.getString(JIRA_FIELD.getString("id_iws")));
            }

            JsonArray comm = fields.getJsonObject("comment")
                    .getJsonArray("comments", new fr.wseduc.webutils.collections.JsonArray());
            JsonArray jsonCommentArray = new fr.wseduc.webutils.collections.JsonArray();
            for (int i = 0; i < comm.size(); i++) {
                JsonObject comment = comm.getJsonObject(i);
                //Write only if the comment is public
                if (!comment.containsKey("visibility")) {
                    String commentFormated = serializeComment(comment);
                    jsonCommentArray.add(commentFormated);
                }
            }
            jsonPivot.put(Supportpivot.COMM_FIELD, jsonCommentArray);

            if (fields.getString(JIRA_FIELD.getString("status_ent")) != null) {
                jsonPivot.put(Supportpivot.STATUSENT_FIELD,
                        fields.getString(JIRA_FIELD.getString("status_ent")));
            }
            if (fields.getString(JIRA_FIELD.getString("status_iws")) != null) {
                jsonPivot.put(Supportpivot.STATUSIWS_FIELD,
                        fields.getString(JIRA_FIELD.getString("status_iws")));
            }

            String currentStatus = fields.getJsonObject("status").getString("name");

            String currentStatusToIWS;
            currentStatusToIWS = JIRA_STATUS_DEFAULT;
            for (String fieldName : JIRA_STATUS_MAPPING.fieldNames()) {
                if (JIRA_STATUS_MAPPING.getJsonArray(fieldName).contains(currentStatus)) {
                    currentStatusToIWS = fieldName;
                    break;
                }
            }

            jsonPivot.put(Supportpivot.STATUSJIRA_FIELD, currentStatusToIWS);

            if (fields.getString(JIRA_FIELD.getString("creation")) != null) {
                jsonPivot.put(Supportpivot.DATE_CREA_FIELD,
                        fields.getString(JIRA_FIELD.getString("creation")));
            }

            if (fields.getString(JIRA_FIELD.getString("resolution_iws")) != null) {
                jsonPivot.put(Supportpivot.DATE_RESOIWS_FIELD,
                        fields.getString(JIRA_FIELD.getString("resolution_iws")));
            }

            if (fields.getString(JIRA_FIELD.getString("resolution_ent")) != null) {
                jsonPivot.put(Supportpivot.DATE_RESOENT_FIELD,
                        fields.getString(JIRA_FIELD.getString("resolution_ent")));
            }

            if (fields.getString("resolutiondate") != null) {
                String dateFormated = getDateFormatted(fields.getString("resolutiondate"), false);
                jsonPivot.put(Supportpivot.DATE_RESOJIRA_FIELD, dateFormated);
            }

            if (fields.getString(JIRA_FIELD.getString("response_technical")) != null) {
                jsonPivot.put(Supportpivot.TECHNICAL_RESP_FIELD,
                        fields.getString(JIRA_FIELD.getString("response_technical")));
            }

            jsonPivot.put(Supportpivot.ATTRIBUTION_FIELD, Supportpivot.ATTRIBUTION_IWS);

            JsonArray attachments = fields.getJsonArray("attachment", new fr.wseduc.webutils.collections.JsonArray());

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
                                String b64FilePJ = getJiraPjResp.right().getValue().getString("b64Attachment");
                                JsonObject currentPJ = new JsonObject();
                                currentPJ.put(Supportpivot.ATTACHMENT_NAME_FIELD, attachmentInfos.getString("filename"));
                                currentPJ.put(Supportpivot.ATTACHMENT_CONTENT_FIELD, b64FilePJ);
                                allPJConverted.add(currentPJ);
                            } else {
                                handler.handle(getJiraPjResp);
                            }

                            //last attachment handles the response
                            if (nbAttachment.decrementAndGet() <= 0) {
                                jsonPivot.put(Supportpivot.ATTACHMENT_FIELD, allPJConverted);
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
        String attachmentLink = attachmentInfos.getString("content");
        final RequestOptions options = new RequestOptions()
          .setURI(attachmentLink)
          .setMethod(HttpMethod.GET);
        addHeaders(options);
        httpClient.request(options).flatMap(HttpClientRequest::send)
        .onSuccess(response -> {
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    String b64Attachment = encoder.encodeToString(bufferGetInfosTicket.getBytes());
                    handler.handle(new Either.Right<>(
                            new JsonObject().put("status", "OK")
                                            .put("b64Attachment", b64Attachment)));

                });
            } else {
                LOGGER.error("Error when calling URL : " + attachmentLink + ":" + response.statusMessage());
                handler.handle(new Either.Left<>("Error when getting Jira attachment ("+attachmentLink+") information"));
            }
        })
        .onFailure(t -> {
            LOGGER.error("Error when calling URL : " + attachmentLink, t);
            handler.handle(new Either.Left<>("Error when getting Jira attachment ("+attachmentLink+") information"));
        });


    }


    /**
     * Serialize comments : date | author | content
     * @param comment Json Object with a comment to serialize
     * @return String with comment serialized
     */
    private String serializeComment (final JsonObject comment) {
        String content = getDateFormatted(comment.getString("created"), true)
                + " | " + comment.getJsonObject("author").getString("displayName")
                + " | " + getDateFormatted(comment.getString("created"), false)
                + " | " + comment.getString("body");

        String origContent = comment.getString("body");

        return hasToSerialize(origContent) ? content : origContent;
    }

    /**
     * Check if comment must be serialized
     * If it's '|' separated (at least 4 fields)
     * And first field is 14 number (AAAMMJJHHmmSS)
     * Then it must not be serialized
     * @param content Comment to check
     * @return true if the comment has to be serialized
     */
    private boolean hasToSerialize(String content) {
        String[] elements = content.split(Pattern.quote("|"));
        if(elements.length < 4) return true;
        String id = elements[0].trim();
        return ( !id.matches("[0-9]{14}") );
    }

    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private String stringEncode(String in) {
        return new String(in.getBytes(), StandardCharsets.UTF_8);
    }



}