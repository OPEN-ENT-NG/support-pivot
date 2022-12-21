package fr.openent.supportpivot.model.endpoint.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.enums.PriorityEnum;
import fr.openent.supportpivot.helpers.*;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.endpoint.AbstractEndpoint;
import fr.openent.supportpivot.model.jira.*;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.model.status.config.JiraStatusConfig;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import fr.wseduc.webutils.Either;
import io.netty.util.concurrent.SucceededFuture;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.openent.supportpivot.constants.JiraConstants.*;
import static fr.openent.supportpivot.constants.PivotConstants.*;


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
            log.error(String.format("[SupportPivot@%s::JiraEndpoint] Invalid uri %s", this.getClass().getSimpleName(), e.getMessage()));
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
                    log.error(String.format("[SupportPivot@%s::getPivotTicket] Fail to get jira ticket %s", this.getClass().getSimpleName(), response.statusCode()));
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
            JiraSearch jiraSearch = new JiraSearch(new JsonObject(body));
            List<Future> futures = new ArrayList<>();
            List<PivotTicket> pivotTickets = new ArrayList<>();
            jiraSearch.getIssues().forEach(issue -> {
                Future<PivotTicket> future = Future.future();
                futures.add(future);
                convertJiraReponseToJsonPivot(issue, event -> {
                    if (event.isRight()) {
                        // filter useful data
                        future.complete(new PivotTicket(event.right().getValue()));
                    } else {
                        log.error(String.format("[SupportPivot@%s::processSearchResponse] Fail to convert ticket %s",
                                this.getClass().getSimpleName(), EitherHelper.getOrNullLeftMessage(event)));
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
        final String id_jira = ticketData.getString(Field.ID_JIRA);

        this.getJiraTicketByJiraId(id_jira, result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
            } else {
                HttpClientResponse response = result.result();
                if (response.statusCode() == 200) {
                    response.bodyHandler(body -> {
                        JiraTicket jiraTicket = new JiraTicket(new JsonObject(body));
                        convertJiraReponseToJsonPivot(jiraTicket, resultPivot -> {
                            if (resultPivot.isRight()) {
                                PivotTicket pivotTicket = new PivotTicket(resultPivot.right().getValue());
                                handler.handle(Future.succeededFuture(pivotTicket));
                            } else {
                                log.error(String.format("[SupportPivot@%s::process] Fail to convert ticket %s",
                                        this.getClass().getSimpleName(), EitherHelper.getOrNullLeftMessage(resultPivot)));
                                handler.handle(Future.failedFuture("process jira ticket failed " + resultPivot.left().getValue()));
                            }
                        });
                    });
                } else {
                    log.error(String.format("[SupportPivot@%s::process] Fail to get jira ticket %s %s",
                            this.getClass().getSimpleName(), response.statusCode(), response.statusMessage()));
                    response.bodyHandler(body -> log.error(response.statusCode() + " " + response.statusMessage() + "  " + body));
                    handler.handle(Future.failedFuture("process jira ticket failed"));
                }
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (ticket.getIdExterne() != null && ticket.getAttribution() != null /*&& ticket.getAttributed().equals(PivotConstants.ATTRIBUTION_NAME)*/) {
            this.getJiraTicketByExternalId(ticket.getIdExterne(), result -> {
                if (result.succeeded()) {
                    HttpClientResponse response = result.result();
                    if (response.statusCode() == 200) {
                        response.bodyHandler(body -> {
                            JsonObject jsonTicket = new JsonObject(body.toString());
                            if (jsonTicket.getInteger(Field.TOTAL) >= 1) {
                                ticket.setIdJira(jsonTicket.getJsonArray(Field.ISSUES).getJsonObject(0).getString(Field.ID));
                            }
                            jiraService.sendToJIRA(ticket)
                                    .onSuccess(pivotTicketResult -> handler.handle(Future.succeededFuture(pivotTicketResult)))
                                    .onFailure(error -> {
                                        log.error(String.format("[SupportPivot@%s::send] Fail to send to jira %s",
                                                this.getClass().getSimpleName(), error.getMessage()));
                                        handler.handle(Future.failedFuture(error));
                                    });
                        });
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to get jira ticket %s %s %s",
                                this.getClass().getSimpleName(), response.request().uri(), response.statusCode(), response.statusMessage()));
                        response.bodyHandler(buffer -> log.error(buffer.getString(0, buffer.length())));
                        handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (externalID: " + ticket.getIdExterne() + ")"));
                    }
                } else {
                    log.error(String.format("[SupportPivot@%s::send] Error when getJiraTicket %s",
                            this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(result)));
                    handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (externalID: " + ticket.getIdExterne()));
                }
            });
        } else {
            ticket.setIdExterne(ticket.getIdEnt());
            this.getJiraTicketByEntId(ticket.getIdExterne(), result -> {
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
                                        ticket.setIdJira(issue.getJsonObject(0).getString(ID));
                                    }
                                }
                            }
                            jiraService.sendToJIRA(ticket)
                                    .onSuccess(pivotTicketResult -> handler.handle(Future.succeededFuture(pivotTicketResult)))
                                    .onFailure(error -> {
                                        log.error(String.format("[SupportPivot@%s::send] Fail to send to jira %s",
                                                this.getClass().getSimpleName(), error));
                                        handler.handle(Future.failedFuture(error));
                                    });
                        });
                    } else {
                        log.error(String.format("[SupportPivot@%s::send] Fail to get jira ticket %s %s %s",
                                this.getClass().getSimpleName(), response.request().uri(), response.statusCode(), response.statusMessage()));
                        response.bodyHandler(buffer -> log.error(buffer.getString(0, buffer.length())));
                        String message = String.format("[SupportPivot@%s::send] Supportpivot A problem occurred when trying to get ticket from jira (externalID: " +
                                ticket.getIdExterne() + " ) : %s", this.getClass().getSimpleName(), result.cause());
                        handler.handle(Future.failedFuture(message));
                    }

                } else {
                    String message = String.format("[SupportPivot@%s::send] Supportpivot A problem occurred when trying to get ticket from jira (externalID: " +
                            ticket.getIdExterne() + " ) : %s", this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(result));
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
                            this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(result)));
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


    //todo use PivotTicket model and dont use handler
    public void convertJiraReponseToJsonPivot(final JiraTicket jiraTicket, final Handler<Either<String, JsonObject>> handler) {
        ConfigModel config = ConfigManager.getInstance().getConfig();
        Map<String, String> jiraCustomFields = config.getJiraCustomFields();
        JiraStatusConfig defaultJiraStatusConfig = config.getDefaultJiraStatus();
        List<JiraStatusConfig> jiraStatusConfigMapping = config.getJiraStatusMapping();

        JiraFields fields = jiraTicket.getFields();

        final JsonObjectSafe jsonPivot = new JsonObjectSafe();

        jsonPivot.putSafe(Field.ID_JIRA, jiraTicket.getKey());
        jsonPivot.putSafe(Field.COLLECTIVITE, config.getCollectivity());
        jsonPivot.putSafe(Field.ACADEMIE, config.getCollectivity());
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

            jsonPivot.putSafe(Field.CREATION, fields.getCreated());
            jsonPivot.putSafe(Field.MAJ, fields.getUpdated());
            jsonPivot.put(Field.DEMANDEUR, fields.getCustomFields(stringEncode(creatorField), ""));

            jsonPivot.putSafe(Field.TYPE_DEMANDE, fields.getIssuetype() == null ? null : fields.getIssuetype().getName());
            jsonPivot.putSafe(Field.TITRE, fields.getSummary());
            jsonPivot.putSafe(Field.UAI, fields.getCustomFields(uaiField, ""));
            jsonPivot.put(Field.DESCRIPTION, fields.getDescription());

            String currentPriority = fields.getPriority().getName();
            currentPriority = PriorityEnum.getValue(currentPriority).getPivotName();

            jsonPivot.put(Field.PRIORITE, currentPriority);

            jsonPivot.putSafe(Field.MODULES, new JsonArray(fields.getLabels()).copy());

            jsonPivot.put(Field.ID_ENT, fields.getCustomFields(idEntField, ""));

            if (fields.getComment() != null) {
                List<JiraComment> jiraCommentList = fields.getComment().getComments();
                List<String> commentString = jiraCommentList.stream()
                        .filter(jiraComment -> jiraComment.getVisibility() == null)
                        .map(this::serializeComment)
                        .collect(Collectors.toList());
                jsonPivot.put(Field.COMMENTAIRES, new JsonArray(commentString));
            }

            jsonPivot.putSafe(Field.STATUT_ENT, fields.getCustomFields(statusEntField, ""));

            String currentStatus = fields.getStatus().getName();

            String currentStatusToIWS = jiraStatusConfigMapping.stream()
                    .filter(jiraStatusConfig -> jiraStatusConfig.contains(currentStatus))
                    .map(JiraStatusConfig::getKey)
                    .findFirst()
                    .orElse(defaultJiraStatusConfig.getKey());

            jsonPivot.put(Field.STATUT_JIRA, currentStatusToIWS);


            jsonPivot.putSafe(Field.DATE_CREATION, fields.getCustomFields(creationField, null));
            jsonPivot.putSafe(Field.DATE_RESOLUTION_ENT, fields.getCustomFields(resolutionEnt, null));
            jsonPivot.putSafe(Field.RESPONSE_TECHNICAL, fields.getCustomFields(responseTechnical, null));
            jsonPivot.putSafe(Field.ID_EXTERNE, fields.getCustomFields(idExternalField, null));
            jsonPivot.putSafe(Field.STATUT_EXTERNE, fields.getCustomFields(statusExterne, null));

            if (fields.getResolutiondate() != null && !fields.getResolutiondate().isEmpty()) {
                String dateFormated = getDateFormatted(fields.getResolutiondate(), false);
                jsonPivot.put(Field.DATE_RESOLUTION_JIRA, dateFormated);
            }

            jsonPivot.put(Field.ATTRIBUTION, ATTRIBUTION_IWS);

            //if no attachment handle the response
            if (fields.getAttachment().isEmpty()) {
                handler.handle(new Either.Right<>(jsonPivot));
                return;
            }

            Map<JiraAttachment, Future<String>> attachmentsPJFutureMap = new HashMap<>();

            Future<String> future = Future.succeededFuture();

            for (JiraAttachment jiraAttachment : fields.getAttachment()) {
                future = future.compose(res -> this.getJiraPJ(jiraAttachment));
                attachmentsPJFutureMap.put(jiraAttachment, future);
            }

            future.onSuccess(res -> {
                        final JsonArray allPJConverted = new JsonArray();
                        attachmentsPJFutureMap.forEach((attachment, PJFuture) -> {
                            if (PJFuture.succeeded()) {
                                JsonObject currentPJ = new JsonObject();
                                currentPJ.put(Field.NOM, attachment.getFilename());
                                currentPJ.put(Field.CONTENU, PJFuture.result());
                                allPJConverted.add(currentPJ);
                            } else {
                                log.error(String.format("[SupportPivot@%s::convertJiraReponseToJsonPivot] Fail to get jira PJ %s",
                                        this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(PJFuture)));
                            }
                        });
                        jsonPivot.put(Field.PJ, allPJConverted);
                        handler.handle(new Either.Right<>(jsonPivot));
                    })
                    .onFailure(event -> {
                        log.error((String.format("[SupportPivot@%s::convertJiraReponseToJsonPivot] Fail to get all jira PJ %s",
                                this.getClass().getSimpleName(), event.getMessage())));
                        handler.handle(new Either.Left<>(event.getMessage()));
                    });
        }
    }

    private Future<String> getJiraPJ(JiraAttachment jiraAttachment) {
        Promise<String> promise = Promise.promise();

        String attachmentLink = jiraAttachment.getContent();

        httpClient.createGetRequest(attachmentLink).startRequest(result -> {
            if (result.succeeded()) {
                if (result.result().statusCode() == 200) {
                    result.result().bodyHandler(bufferGetInfosTicket -> {
                        String b64Attachment = encoder.encodeToString(bufferGetInfosTicket.getBytes());
                        promise.complete(b64Attachment);
                    });
                } else {
                    log.error(String.format("[SupportPivot@%s::getJiraPJ] Error when calling URL: %s %s %s",
                            this.getClass().getSimpleName(), attachmentLink, result.result().statusCode(), AsyncResultHelper.getOrNullFailMessage(result)));
                    result.result().bodyHandler(body -> log.error(body.toString()));
                    promise.fail("Error when getting Jira attachment (" + attachmentLink + ") information");
                }
            } else {
                log.error(String.format("[SupportPivot@%s::getJiraPJ] Error when calling URL: %s %s",
                        this.getClass().getSimpleName(), attachmentLink, AsyncResultHelper.getOrNullFailMessage(result)));
                promise.fail("Error when getting Jira attachment (" + attachmentLink + ") information");
            }
        });

        return promise.future();
    }

    /**
     * Serialize comments : date | author | content
     *
     * @param comment JiraComment comment
     * @return String with comment serialized
     */
    private String serializeComment(final JiraComment comment) {
        String content = getDateFormatted(comment.getCreated(), true)
                + " | " + comment.getAuthor().getDisplayName()
                + " | " + getDateFormatted(comment.getCreated(), false)
                + " | " + comment.getBody();

        String origContent = comment.getBody();

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
