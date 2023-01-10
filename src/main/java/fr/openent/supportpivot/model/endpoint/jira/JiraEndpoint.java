package fr.openent.supportpivot.model.endpoint.jira;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.enums.PriorityEnum;
import fr.openent.supportpivot.helpers.AsyncResultHelper;
import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.helpers.HttpRequestHelper;
import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.ConfigModel;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.jira.*;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import fr.openent.supportpivot.model.status.config.JiraStatusConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class JiraEndpoint implements Endpoint<JiraTicket, JiraSearch> {
    private static final Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();

    private static final Logger log = LoggerFactory.getLogger(JiraEndpoint.class);

    public JiraEndpoint() {
    }

    @Override
    public Future<JiraTicket> getPivotTicket(JiraSearch jiraSearch) {
        Promise<JiraTicket> promise = Promise.promise();
        Future<JiraSearchResult> future = Future.failedFuture("Searcher malformed");

        if (jiraSearch.getIdJira() != null) {
            future = this.getJiraTicketByJiraId(jiraSearch.getIdJira());
        } else if (jiraSearch.getIdExterne() != null) {
            future = this.getJiraTicketByExternalId(jiraSearch);
        } else if (jiraSearch.getIdEnt() != null) {
            future = this.getJiraTicketByEntId(jiraSearch);
        }

        future
                .onSuccess(jiraSearchResult -> {
                    if (jiraSearchResult.getIssues().isEmpty()) {
                        promise.complete(new JiraTicket());
                    } else {
                        promise.complete(jiraSearchResult.getIssues().get(0));
                    }
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    @Override
    public Future<List<JiraTicket>> getPivotTicketList(JiraSearch searchTicket) {
        Promise<List<JiraTicket>> promise = Promise.promise();
        URI uri = prepareSearchRequest(searchTicket);

        executeJiraRequest(uri, 200)
                .onSuccess(body -> {
                    JiraSearchResult jiraSearchResult = new JiraSearchResult(new JsonObject(body));
                    promise.complete(jiraSearchResult.getIssues());
                })
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getPivotTicket] Fail to get jira ticket %s", this.getClass().getSimpleName(), error.getMessage()));
                    promise.fail(error);
                });

        return promise.future();
    }

    @Override
    public Future<JiraTicket> setTicket(PivotTicket ticket) {
        Promise<JiraTicket> promise = Promise.promise();

        ServiceManager.getInstance().getJiraService().sendToJIRA(ticket)
                .onSuccess(pivotTicket -> promise.complete(new JiraTicket(pivotTicket)))
                .onFailure(promise::fail);

        return promise.future();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Future<PivotTicket> toPivotTicket(JiraTicket jiraTicket) {
        Promise<PivotTicket> promise = Promise.promise();

        ConfigModel config = ConfigManager.getInstance().getConfig();
        Map<String, String> jiraCustomFields = config.getJiraCustomFields();
        JiraStatusConfig defaultJiraStatusConfig = config.getDefaultJiraStatus();
        List<JiraStatusConfig> jiraStatusConfigMapping = config.getJiraStatusMapping();

        JiraFields fields = jiraTicket.getFields();

        PivotTicket pivotTicket = new PivotTicket();

        pivotTicket.setIdJira(jiraTicket.getKey());
        pivotTicket.setCollectivite(config.getCollectivity());
        pivotTicket.setAcademie(config.getCollectivity());
        if (fields == null) {
            promise.complete(pivotTicket);
        } else {
            String creatorField = jiraCustomFields.getOrDefault(Field.CREATOR, "");
            String creationField = jiraCustomFields.getOrDefault(Field.CREATION, "");
            String uaiField = jiraCustomFields.getOrDefault(Field.UAI, "");
            String idEntField = jiraCustomFields.getOrDefault(Field.ID_ENT, "");
            String statusEntField = jiraCustomFields.getOrDefault(Field.STATUS_ENT, "");
            String resolutionEnt = jiraCustomFields.getOrDefault(Field.RESOLUTION_ENT, "");
            String responseTechnical = jiraCustomFields.getOrDefault(Field.RESPONSE_TECHNICAL, "");
            String idExternalField = jiraCustomFields.getOrDefault(Field.ID_EXTERNE, "");
            String statusExterne = jiraCustomFields.getOrDefault(Field.STATUS_EXTERNE, "");

            pivotTicket.setCreation(fields.getCreated());
            pivotTicket.setMaj(fields.getUpdated());
            pivotTicket.setDemandeur(fields.getCustomFields(stringEncode(creatorField), ""));

            pivotTicket.setTypeDemande(fields.getIssuetype() == null ? null : fields.getIssuetype().getName());
            pivotTicket.setTitre(fields.getSummary());
            pivotTicket.setUai(fields.getCustomFields(uaiField, ""));
            pivotTicket.setDescription(fields.getDescription());

            String currentPriority = fields.getPriority().getName();
            currentPriority = PriorityEnum.getValue(currentPriority).getPivotName();

            pivotTicket.setPriorite(currentPriority);

            pivotTicket.setModules(new ArrayList<>(fields.getLabels()));

            pivotTicket.setIdEnt(fields.getCustomFields(idEntField, ""));

            if (fields.getComment() != null) {
                List<JiraComment> jiraCommentList = fields.getComment().getComments();
                List<String> commentString = jiraCommentList.stream()
                        .filter(jiraComment -> jiraComment.getVisibility() == null)
                        .map(this::serializeComment)
                        .collect(Collectors.toList());
                pivotTicket.setCommentaires(commentString);
            }

            pivotTicket.setStatutEnt(fields.getCustomFields(statusEntField, ""));

            String currentStatus = jiraStatusConfigMapping.stream()
                    .filter(jiraStatusConfig -> jiraStatusConfig.contains(fields.getStatus().getName()))
                    .map(JiraStatusConfig::getKey)
                    .findFirst()
                    .orElse(defaultJiraStatusConfig.getKey());

            pivotTicket.setStatutJira(currentStatus);


            pivotTicket.setDateCreation(fields.getCustomFields(creationField, null));
            pivotTicket.setDateResolutionEnt(fields.getCustomFields(resolutionEnt, null));
            pivotTicket.setIdExterne(fields.getCustomFields(idExternalField, null));
            pivotTicket.setStatutExterne(fields.getCustomFields(statusExterne, null));

            if (fields.getResolutiondate() != null && !fields.getResolutiondate().isEmpty()) {
                String dateFormated = getDateFormatted(fields.getResolutiondate(), false);
                pivotTicket.setDateResolutionJira(dateFormated);
            }
            //Todo est ce utile ?
            pivotTicket.setAttribution(Field.RECTORAT);

            //if no attachment handle the response
            if (fields.getAttachment().isEmpty()) {
                return Future.succeededFuture(pivotTicket);
            }

            Map<JiraAttachment, Future<String>> attachmentsPJFutureMap = new HashMap<>();

            Future<String> future = Future.succeededFuture();

            for (JiraAttachment jiraAttachment : fields.getAttachment()) {
                future = future.compose(res -> this.getJiraPJ(jiraAttachment));
                attachmentsPJFutureMap.put(jiraAttachment, future);
            }

            future.onSuccess(res -> {
                        final List<PivotPJ> allPJConverted = new ArrayList<>();
                        attachmentsPJFutureMap.forEach((attachment, PJFuture) -> {
                            if (PJFuture.succeeded()) {
                                PivotPJ pivotPJ = new PivotPJ();
                                pivotPJ.setNom(attachment.getFilename());
                                pivotPJ.setContenu(PJFuture.result());
                                allPJConverted.add(pivotPJ);
                            } else {
                                log.error(String.format("[SupportPivot@%s::convertJiraReponseToJsonPivot] Fail to get jira PJ %s",
                                        this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(PJFuture)));
                            }
                        });
                        pivotTicket.setPj(allPJConverted);
                        promise.complete(pivotTicket);
                    })
                    .onFailure(event -> {
                        log.error((String.format("[SupportPivot@%s::convertJiraReponseToJsonPivot] Fail to get all jira PJ %s",
                                this.getClass().getSimpleName(), event.getMessage())));
                        promise.fail(event);
                    });
        }

        return promise.future();
    }

    /**
     * Returns the URI allowing to perform a search from the Jira API using a jiraSearch
     *
     * @param jiraSearch the jiraSearch containing the filters to perform the search
     * @return Search URI
     */
    private URI prepareSearchRequest(JiraSearch jiraSearch) {
        JiraFilterBuilder filter = new JiraFilterBuilder();
        Map<String, String> jiraField = ConfigManager.getInstance().getConfig().getJiraCustomFields();
        if (jiraSearch.getAttribution() != null) {
            String customFieldName = jiraField.getOrDefault(Field.ID_EXTERNE, "");
            if (customFieldName == null || customFieldName.isEmpty()) {
                log.error(String.format("[SupportPivot@%s::prepareSearchRequest]: Can not find customFieldFilter %s",
                        this.getClass().getSimpleName(), Field.ID_EXTERNE));
            }
            filter.addAssigneeOrCustomFieldFilter(jiraSearch.getAttribution(),
                    customFieldName, null);
        }
        if (jiraSearch.getDate() != null) {
            filter.addMinUpdateDate(jiraSearch.getDate());
        }
        filter.onlyIds();
        filter.addFieldDates();
        String creationFieldJira = jiraField.getOrDefault(Field.CREATION, "");
        if (creationFieldJira == null || creationFieldJira.isEmpty()) {
            log.error(String.format("[SupportPivot@%s::prepareSearchRequest]: Can not find creationFieldJira",
                    this.getClass().getSimpleName()));
        }
        filter.addFields(creationFieldJira);
        filter.setMaxResults(jiraSearch.getMaxResult());

        return ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
    }

    /**
     * Search for a ticket in Jira using its id
     *
     * @param idJira the jira ticket id
     * @return JiraSearchResult with 1 result corresponding to the desired ticket
     */
    private Future<JiraSearchResult> getJiraTicketByJiraId(String idJira) {
        Promise<JiraSearchResult> promise = Promise.promise();

        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("issue/" + idJira);
        executeJiraRequest(uri, 200)
                .onSuccess(body -> promise.complete(new JiraSearchResult().setIssues(Collections.singletonList(new JiraTicket(new JsonObject(body))))))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getJiraTicketByJiraId] Fail to get ticket %s",
                            this.getClass().getSimpleName(), error));
                    promise.fail(error);
                });

        return promise.future();
    }

    /**
     * Searches for a set of tickets from an externalId
     *
     * @param jiraSearch the jiraSearch which contains the externalId
     * @return a future complete with the search results in JiraSearchResult class
     */
    private Future<JiraSearchResult> getJiraTicketByExternalId(JiraSearch jiraSearch) {
        Promise<JiraSearchResult> promise = Promise.promise();

        String idCustomField = ConfigManager.getInstance().getJiraCustomFieldIdForExternalId().replaceAll(Field.CUSTOMFIELD_, "");
        JiraFilterBuilder filter = new JiraFilterBuilder();
        filter.addCustomfieldFilter(idCustomField, jiraSearch.getIdExterne());
        filter.setMaxResults(jiraSearch.getMaxResult());
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
        executeJiraRequest(uri, 200)
                .onSuccess(body -> promise.complete(new JiraSearchResult(new JsonObject(body))))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getJiraTicketByJiraId] Fail to get ticket %s",
                            this.getClass().getSimpleName(), error));
                    promise.fail(error);
                });

        return promise.future();
    }

    /**
     * Searches for a set of tickets from an entId
     *
     * @param jiraSearch the jiraSearch which contains the entId
     * @return a future complete with the search results in JiraSearchResult class
     */
    private Future<JiraSearchResult> getJiraTicketByEntId(JiraSearch jiraSearch) {
        Promise<JiraSearchResult> promise = Promise.promise();

        String idCustomField = ConfigManager.getInstance().getJiraCustomFieldIdForIdent().replaceAll("customfield_", "");
        JiraFilterBuilder filter = new JiraFilterBuilder();
        filter.addCustomfieldFilter(idCustomField, jiraSearch.getIdEnt());
        filter.setMaxResults(jiraSearch.getMaxResult());
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("search?" + filter.buildSearchQueryString());
        executeJiraRequest(uri, 200)
                .onSuccess(body -> promise.complete(new JiraSearchResult(new JsonObject(body))))
                .onFailure(error -> {
                    log.error(String.format("[SupportPivot@%s::getJiraTicketByJiraId] Fail to get ticket %s",
                            this.getClass().getSimpleName(), error));
                    promise.fail(error);
                });

        return promise.future();
    }


    /**
     * Runs a Jira uri
     *
     * @param uri the URI
     * @param codeExpected if the request status is not this we fail the promise
     * @return a future complete with Buffer
     */
    private Future<Buffer> executeJiraRequest(URI uri, int codeExpected) {
        Promise<Buffer> promise = Promise.promise();

        HttpRequest<Buffer> request = HttpRequestHelper.getJiraAuthRequest(HttpMethod.GET, uri.toString());

        request.send(resultAsyncResult -> {
            if (resultAsyncResult.succeeded()) {
                HttpResponse<Buffer> response = resultAsyncResult.result();
                if (response.statusCode() == codeExpected) {
                    promise.complete(response.body());
                } else {
                    log.error(String.format("[SupportPivot@%s::executeJiraRequest] Fail to execute jira request Request status %s code %s %s",
                            this.getClass().getSimpleName(), response.statusMessage(), response.statusCode(), AsyncResultHelper.getOrNullFailMessage(resultAsyncResult)));
                    promise.fail(String.format("Request status %s code %s", response.statusMessage(), response.statusCode()));
                }
            } else {
                log.error(String.format("[SupportPivot@%s::executeJiraRequest] Fail to execute jira request %s",
                        this.getClass().getSimpleName(), AsyncResultHelper.getOrNullFailMessage(resultAsyncResult)));
                promise.fail(AsyncResultHelper.getOrNullFailMessage(resultAsyncResult));
            }
        });

        return promise.future();
    }

    /**
     * Get a PJ in base64 format from a JiraAttachment
     *
     * @param jiraAttachment the JiraAttachment
     * @return a future complete with base64 result
     */
    private Future<String> getJiraPJ(JiraAttachment jiraAttachment) {
        Promise<String> promise = Promise.promise();

        String attachmentLink = jiraAttachment.getContent();

        HttpRequest<Buffer> httpRequest = HttpRequestHelper.getJiraAuthRequest(HttpMethod.GET, attachmentLink);

        httpRequest.send(result -> {
            if (result.succeeded()) {
                HttpResponse<Buffer> response = result.result();
                if (response.statusCode() == 200) {
                    String b64Attachment = encoder.encodeToString(response.body().getBytes());
                    promise.complete(b64Attachment);
                } else {
                    log.error(String.format("[SupportPivot@%s::getJiraPJ] Error when calling URL: %s %s %s",
                            this.getClass().getSimpleName(), attachmentLink, result.result().statusCode(), AsyncResultHelper.getOrNullFailMessage(result)));
                    log.error(response.bodyAsString());
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
