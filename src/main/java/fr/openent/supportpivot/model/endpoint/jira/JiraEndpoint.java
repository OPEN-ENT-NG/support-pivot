package fr.openent.supportpivot.model.endpoint.jira;

import fr.openent.supportpivot.constants.EntConstants;
import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.helpers.*;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.status.JiraStatus;
import fr.openent.supportpivot.model.endpoint.AbstractEndpoint;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.*;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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

import static fr.openent.supportpivot.constants.JiraConstants.ATTRIBUTION_FILTERNAME;
import static fr.openent.supportpivot.constants.JiraConstants.ATTRIBUTION_FILTER_DATE;
import static fr.openent.supportpivot.constants.JiraConstants.ATTRIBUTION_FILTER_CUSTOMFIELD;
import static fr.openent.supportpivot.constants.PivotConstants.*;
import static fr.openent.supportpivot.model.ticket.PivotTicket.*;


public class JiraEndpoint extends AbstractEndpoint {


    private PivotHttpClient httpClient;
    private final JiraService jiraService;
    private static final Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();


    private static final Logger log = LoggerFactory.getLogger(JiraEndpoint.class);

    public JiraEndpoint(HttpClientService httpClientService, JiraService jiraService) {
        try {
            this.httpClient = httpClientService.getHttpClient(ConfigManager.getInstance().getConfig().getJiraHost());
            httpClient.setBasicAuth(ConfigManager.getInstance().getConfig().getJiraLogin(), ConfigManager.getInstance().getConfig().getJiraPasswd());

        } catch (URISyntaxException e) {
            log.error(String.format("[SupportPivot@%s::JiraEndpoint] Invalid uri %s", this.getClass().getName(), e.getMessage()));
        }

        this.jiraService = jiraService;
    }

    @Override
    public void getPivotTicket(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
        URI uri = prepareSearchRequest(data);

        executeJiraRequest(uri, getJiraTicketResult -> {
            if (getJiraTicketResult.succeeded()) {
                HttpClientResponse response = getJiraTicketResult.result();
                if (response.statusCode() == 200) {
                    processSearchResponse(response, handler);
                } else {
                    log.error(String.format("[SupportPivot@%s::getPivotTicket] Fail to get jira ticket %s", this.getClass().getName(), response.statusCode()));
                    response.bodyHandler(body -> log.error(response.statusCode() + " " + response.statusMessage() + "  " + body));
                    handler.handle(Future.failedFuture("process jira ticket failed"));
                }
            } else {
                handler.handle(Future.failedFuture(getJiraTicketResult.cause()));
            }

        });
    }

    private URI prepareSearchRequest(JsonObject data) {
        JiraFilterBuilder filter = new JiraFilterBuilder();
        Map<String, String> jiraField = ConfigManager.getInstance().getConfig().getJiraCustomFields();
        if (data.containsKey(ATTRIBUTION_FILTERNAME)) {
            String customFieldFilter = data.getString(ATTRIBUTION_FILTER_CUSTOMFIELD, "");
            if (customFieldFilter.isEmpty()) {
                filter.addAssigneeFilter(data.getString(ATTRIBUTION_FILTERNAME));
            } else {
                String customFieldName = jiraField.getOrDefault(customFieldFilter, "");
                if (customFieldName == null || customFieldName.isEmpty()) {
                    log.error(String.format("[SupportPivot@%s::prepareSearchRequest]: Can not find customFieldFilter %s",
                            this.getClass().getSimpleName(), customFieldFilter));
                }
                filter.addAssigneeOrCustomFieldFilter(data.getString(ATTRIBUTION_FILTERNAME),
                        customFieldName, null);
            }
        }
        if (data.containsKey(ATTRIBUTION_FILTER_DATE)) {
            filter.addMinUpdateDate(data.getString(ATTRIBUTION_FILTER_DATE));
        }
        filter.onlyIds();
        filter.addFieldDates();
        String creationFieldJira = jiraField.getOrDefault(Field.CREATION, "");
        if (creationFieldJira == null || creationFieldJira.isEmpty()) {
            log.error(String.format("[SupportPivot@%s::prepareSearchRequest]: Can not find creationFieldJira",
                    this.getClass().getSimpleName()));
        }
        filter.addFields(creationFieldJira);

        return ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
    }

    private void processSearchResponse(HttpClientResponse response, Handler<AsyncResult<List<PivotTicket>>> handler) {
        response.bodyHandler(body -> {
            JsonObject jsonTicket = new JsonObject(body.toString());
            List<Future> futures = new ArrayList<>();
            List<PivotTicket> pivotTickets = new ArrayList<>();
            jsonTicket.getJsonArray(Field.ISSUES).forEach(issue -> {
                Future<PivotTicket> future = Future.future();
                futures.add(future);
                convertJiraReponseToJsonPivot((JsonObject) issue, event -> {
                    if (event.isRight()) {
                        // filter useful data
                        future.complete(new PivotTicket().setJsonObject(event.right().getValue()));
                    } else {
                        log.error(String.format("[SupportPivot@%s::processSearchResponse] Fail to convert ticket %s",
                                this.getClass().getName(), EitherHelper.getOrNullLeftMessage(event)));
                        future.fail(event.left().getValue());
                    }
                });
            });

            CompositeFuture.join(futures).setHandler(event -> {
                if (event.succeeded()) {
                    for (Future future : futures) {
                        if (future.succeeded() && future.result() != null) {
                            pivotTickets.add((PivotTicket) future.result());
                        }
                    }
                    handler.handle(Future.succeededFuture(pivotTickets));
                } else {
                    handler.handle(Future.failedFuture(event.cause().getMessage()));
                }
            });
        });
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        final String id_jira = ticketData.getString(Field.IDJIRA);

        this.getJiraTicketByJiraId(id_jira, result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
            } else {
                HttpClientResponse response = result.result();
                if (response.statusCode() == 200) {
                    response.bodyHandler(body -> {
                        JsonObject jsonTicket = new JsonObject(body.toString());
                        convertJiraReponseToJsonPivot(jsonTicket, resultPivot -> {
                            if (resultPivot.isRight()) {
                                PivotTicket pivotTicket = new PivotTicket();
                                pivotTicket.setJsonObject(resultPivot.right().getValue());
                                handler.handle(Future.succeededFuture(pivotTicket));
                            } else {
                                log.error(String.format("[SupportPivot@%s::processSearchResponse] Fail to convert ticket %s",
                                        this.getClass().getName(), EitherHelper.getOrNullLeftMessage(resultPivot)));
                                handler.handle(Future.failedFuture("process jira ticket failed " + resultPivot.left().getValue()));
                            }
                        });
                    });
                } else {
                    log.error(String.format("[SupportPivot@%s::processSearchResponse] Fail to get jira ticket %s %s",
                            this.getClass().getName(), response.statusCode(), response.statusMessage()));
                    response.bodyHandler(body -> log.error(response.statusCode() + " " + response.statusMessage() + "  " + body));
                    handler.handle(Future.failedFuture("process jira ticket failed"));
                }
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (ticket.getExternalId() != null && ticket.getAttributed() != null /*&& ticket.getAttributed().equals(PivotConstants.ATTRIBUTION_NAME)*/) {
            this.getJiraTicketByExternalId(ticket.getExternalId(), result -> {
                if (result.succeeded()) {
                    HttpClientResponse response = result.result();
                    if (response.statusCode() == 200) {
                        response.bodyHandler(body -> {
                            JsonObject jsonTicket = new JsonObject(body.toString());
                            if (jsonTicket.getInteger(Field.TOTAL) >= 1) {
                                ticket.setJiraId(jsonTicket.getJsonArray(Field.ISSUES).getJsonObject(0).getString(Field.ID));
                            }
                            jiraService.sendToJIRA(ticket.getJsonTicket(), sendToJiraResult -> {
                                if (sendToJiraResult.isRight()) {
                                    PivotTicket pivotTicket = new PivotTicket();
                                    pivotTicket.setJsonObject(sendToJiraResult.right().getValue().getJsonObject(Field.JSONPIVOTCOMPLETED));
                                    handler.handle(Future.succeededFuture(pivotTicket));
                                } else {
                                    log.error(String.format("[SupportPivot@%s::send] Fail to send to jira %s",
                                            this.getClass().getName(), EitherHelper.getOrNullLeftMessage(sendToJiraResult)));
                                    handler.handle(Future.failedFuture(sendToJiraResult.left().getValue()));
                                }
                            });
                        });
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to get jira ticket %s %s %s",
                                this.getClass().getName(), response.request().uri(), response.statusCode(), response.statusMessage()));
                        response.bodyHandler(buffer -> log.error(buffer.getString(0, buffer.length())));
                        handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (externalID: " + ticket.getExternalId() + ")"));
                    }

                } else {
                    log.error(String.format("[SupportPivot@%s::send] Error when getJiraTicket %s",
                            this.getClass().getName(), AsyncResultHelper.getOrNullFailMessage(result)));
                    handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (externalID: " + ticket.getExternalId()));
                }
            });
        } else {

            ticket.setExternalId(ticket.getJsonTicket().getString(EntConstants.IDENT_FIELD));
            this.getJiraTicketByEntId(ticket.getExternalId(), result -> {
                if (result.succeeded()) {
                    HttpClientResponse response = result.result();
                    if (response.statusCode() == 200) {
                        response.bodyHandler(body -> {
                            if (body != null) {
                                JsonObject jsonTicket = new JsonObject(body.toString());
                                if (jsonTicket.getInteger(JiraConstants.TOTAL) >= 1) {
                                    JsonArray issue = jsonTicket.getJsonArray(ISSUES, new JsonArray());
                                    if (issue.isEmpty()) {
                                        String message = String.format("[SupportPivot@%s::send] Supportpivot Issues is Empty",
                                                this.getClass().getSimpleName());
                                        log.error(message);
                                    } else {
                                        ticket.setJiraId(jsonTicket.getJsonArray(ISSUES, new JsonArray()).getJsonObject(0).getString(ID));
                                    }
                                }
                            }
                            jiraService.sendToJIRA(ticket.getJsonTicket(), sendToJiraResult -> {
                                if (sendToJiraResult.isRight()) {
                                    PivotTicket pivotTicket = new PivotTicket();
                                    pivotTicket.setJsonObject(sendToJiraResult.right().getValue().getJsonObject(JSONPIVOTCOMPLETED, new JsonObject()));
                                    handler.handle(Future.succeededFuture(pivotTicket));
                                } else {
                                    log.error(String.format("[SupportPivot@%s::send] Fail to send to jira %s",
                                            this.getClass().getName(), EitherHelper.getOrNullLeftMessage(sendToJiraResult)));
                                    handler.handle(Future.failedFuture(sendToJiraResult.left().getValue()));
                                }
                            });
                        });
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to get jira ticket %s %s %s",
                                this.getClass().getName(), response.request().uri(), response.statusCode(), response.statusMessage()));
                        response.bodyHandler(buffer -> log.error(buffer.getString(0, buffer.length())));
                        String message = String.format("[SupportPivot@%s::send] Supportpivot A problem occurred when trying to get ticket from jira (externalID: " +
                                ticket.getExternalId() + " ) : %s", this.getClass().getSimpleName(), result.cause());
                        handler.handle(Future.failedFuture(message));
                    }

                } else {
                    String message = String.format("[SupportPivot@%s::send] Supportpivot A problem occurred when trying to get ticket from jira (externalID: " +
                            ticket.getExternalId() + " ) : %s", this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(result));
                    log.error(message);
                    handler.handle(Future.failedFuture(message));
                }
            });
        }
    }

    private void getJiraTicketByJiraId(String idJira, Handler<AsyncResult<HttpClientResponse>> handler) {
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("issue/" + idJira);
        executeJiraRequest(uri, handler);
    }

    private void getJiraTicketByExternalId(String idExternal, Handler<AsyncResult<HttpClientResponse>> handler) {
        String idCustomField = ConfigManager.getInstance().getJiraCustomFieldIdForExternalId().replaceAll("customfield_", "");
        JiraFilterBuilder filter = new JiraFilterBuilder();
        filter.addCustomfieldFilter(idCustomField, idExternal);
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
        executeJiraRequest(uri, handler);
    }

    private void getJiraTicketByEntId(String idEnt, Handler<AsyncResult<HttpClientResponse>> handler) {
        String idCustomField = ConfigManager.getInstance().getjiraCustomFieldIdForIdent().replaceAll("customfield_", "");
        JiraFilterBuilder filter = new JiraFilterBuilder();
        filter.addCustomfieldFilter(idCustomField, idEnt);
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
        executeJiraRequest(uri, handler);
    }


    private void executeJiraRequest(URI uri, Handler<AsyncResult<HttpClientResponse>> handler) {
        try {

            PivotHttpClientRequest sendingRequest = this.httpClient.createRequest("GET", uri.toString(), "");
            setHeaderRequest(sendingRequest);
            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    log.error(String.format("[SupportPivot@%s::executeJiraRequest] Fail to execute jira request %s",
                            this.getClass().getName(), AsyncResultHelper.getOrNullFailMessage(result)));
                    handler.handle(Future.failedFuture(AsyncResultHelper.getOrNullFailMessage(result)));
                }
            });
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void setHeaderRequest(PivotHttpClientRequest request) {
        HttpClientRequest clientRequest = request.getHttpClientRequest();
        clientRequest.putHeader("Authorization", "Basic " + encoder.encodeToString(ConfigManager.getInstance().getJiraAuthInfo().getBytes()))
                .setFollowRedirects(true);
        if (!clientRequest.headers().contains("Content-Type")) {
            clientRequest.putHeader("Content-Type", "application/json");
        }
    }


    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    public void convertJiraReponseToJsonPivot(final JsonObject jiraTicket,
                                              final Handler<Either<String, JsonObject>> handler) {
        ConfigModel config = ConfigManager.getInstance().getConfig();
        Map<String, String> jiraCustomFields = config.getJiraCustomFields();
        JiraStatus defaultJiraStatus = config.getDefaultJiraStatus();
        List<JiraStatus> jiraStatusMapping = config.getJiraStatusMapping();

        JsonObject fields = jiraTicket.getJsonObject(Field.FIELDS);

        final JsonObjectSafe jsonPivot = new JsonObjectSafe();

        jsonPivot.putSafe(IDJIRA_FIELD, jiraTicket.getString(Field.KEY));
        jsonPivot.putSafe(COLLECTIVITY_FIELD, config.getCollectivity());
        jsonPivot.putSafe(ACADEMY_FIELD, config.getCollectivity());
        if (fields == null) {
            handler.handle(new Either.Right<>(jsonPivot));
        } else {
            String creatorField = jiraCustomFields.getOrDefault(Field.CREATOR, "");
            String creationField = jiraCustomFields.getOrDefault(Field.CREATION, "");
            String uaiField = jiraCustomFields.getOrDefault(Field.UAI, "");
            String idEntField = jiraCustomFields.getOrDefault(Field.ID_ENT, "");
            String statusEntField = jiraCustomFields.getOrDefault(Field.STATUS_ENT, "");
            String resolutionIws = jiraCustomFields.getOrDefault(Field.RESOLUTION_IWS, "");
            String resolutionEnt = jiraCustomFields.getOrDefault(Field.RESOLUTION_ENT, "");
            String responseTechnical = jiraCustomFields.getOrDefault(Field.RESPONSE_TECHNICAL, "");
            String idExternalField = jiraCustomFields.getOrDefault(Field.ID_EXTERNE, "");
            String statusExterne = jiraCustomFields.getOrDefault(Field.STATUS_EXTERNE, "");

            jsonPivot.putSafe(RAWDATE_CREA_FIELD, fields.getString(Field.CREATED));
            jsonPivot.putSafe(RAWDATE_UPDATE_FIELD, fields.getString(Field.UPDATED));
            jsonPivot.put(CREATOR_FIELD,
                    fields.getString(stringEncode(creatorField), ""));

            jsonPivot.putSafe(TICKETTYPE_FIELD, fields
                    .getJsonObject(Field.ISSUETYPE, new JsonObject()).getString(Field.NAME));
            jsonPivot.putSafe(TITLE_FIELD, fields.getString(Field.SUMMARY));
            jsonPivot.putSafe(UAI_FIELD, fields.getString(uaiField));
            jsonPivot.put(DESCRIPTION_FIELD, fields.getString(Field.DESCRIPTION, ""));

            String currentPriority = fields.getJsonObject(Field.PRIORITY, new JsonObject()).getString(Field.NAME, "");
            //Todo use Enum to remove magic String
            switch (currentPriority) {
                case "High":
                case "Majeure":
                    currentPriority = PRIORITY_MAJOR;
                    break;
                case "Highest":
                case "Bloquante":
                    currentPriority = PRIORITY_BLOCKING;
                    break;
                case "Lowest":
                case "Mineure":
                default:
                    currentPriority = PRIORITY_MINOR;
                    break;
            }

            jsonPivot.put(PRIORITY_FIELD, currentPriority);

            jsonPivot.putSafe(MODULES_FIELD, fields.getJsonArray(Field.LABELS));

            jsonPivot.put(ID_FIELD, fields.getString(idEntField, ""));

            if (fields.containsKey(Field.COMMENT)) {
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
            }

            jsonPivot.putSafe(STATUSENT_FIELD, fields.getString(statusEntField));

            String currentStatus = fields.getJsonObject(Field.STATUS, new JsonObject()).getString(Field.NAME, "");

            String currentStatusToIWS = jiraStatusMapping.stream()
                    .filter(jiraStatus -> jiraStatus.contains(currentStatus))
                    .map(JiraStatus::getKey)
                    .findFirst()
                    .orElse(defaultJiraStatus.getKey());

            jsonPivot.put(STATUSJIRA_FIELD, currentStatusToIWS);


            jsonPivot.putSafe(DATE_CREA_FIELD, fields.getString(creationField));
            jsonPivot.putSafe(DATE_RESOIWS_FIELD, fields.getString(resolutionIws));
            jsonPivot.putSafe(DATE_RESO_FIELD, fields.getString(resolutionEnt));
            jsonPivot.putSafe(TECHNICAL_RESP_FIELD, fields.getString(responseTechnical));
            jsonPivot.putSafe(IDEXTERNAL_FIELD, fields.getString(idExternalField, null));
            jsonPivot.putSafe(STATUSEXTERNAL_FIELD, fields.getString(statusExterne, null));

            if (fields.getString(Field.RESOLUTIONDATE) != null) {
                String dateFormated = getDateFormatted(fields.getString(Field.RESOLUTIONDATE), false);
                jsonPivot.put(DATE_RESOJIRA_FIELD, dateFormated);
            }

            jsonPivot.put(ATTRIBUTION_FIELD, ATTRIBUTION_IWS);

            JsonArray attachments = fields.getJsonArray(Field.ATTACHMENT, new JsonArray());

            final JsonArray allPJConverted = new JsonArray();
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

        final PivotHttpClientRequest getAttachmentrequest = httpClient.createGetRequest(attachmentLink);
        getAttachmentrequest.startRequest(result -> {
            if (result.succeeded()) {
                if (result.result().statusCode() == 200) {
                    result.result().bodyHandler(bufferGetInfosTicket -> {
                        String b64Attachment = encoder.encodeToString(bufferGetInfosTicket.getBytes());
                        handler.handle(new Either.Right<>(
                                new JsonObject().put(Field.STATUS, Field.OK)
                                        .put(Field.B64ATTACHMENT, b64Attachment)));

                    });
                } else {
                    log.error(String.format("[SupportPivot@%s::getJiraPJ] Error when calling URL: %s %s %s",
                            this.getClass().getName(), attachmentLink, result.result().statusCode(), AsyncResultHelper.getOrNullFailMessage(result)));
                    result.result().bodyHandler(body -> log.error(body.toString()));
                    handler.handle(new Either.Left<>("Error when getting Jira attachment (" + attachmentLink + ") information"));
                }
            } else {
                log.error(String.format("[SupportPivot@%s::getJiraPJ] Error when calling URL: %s %s",
                        this.getClass().getName(), attachmentLink, AsyncResultHelper.getOrNullFailMessage(result)));
                handler.handle(new Either.Left<>("Error when getting Jira attachment (" + attachmentLink + ") information"));
            }
        });
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
            log.error("Support : error when parsing date");
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
}
