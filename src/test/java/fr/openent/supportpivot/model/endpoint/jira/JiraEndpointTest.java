package fr.openent.supportpivot.model.endpoint.jira;

import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModelTest;
import fr.openent.supportpivot.model.jira.JiraAttachment;
import fr.openent.supportpivot.model.jira.JiraTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraServiceImpl;
import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.impl.HttpClientResponseImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;

import java.net.URI;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({JiraEndpoint.class, JiraServiceImpl.class}) //Prepare the static class you want to test
public class JiraEndpointTest {
    JiraEndpoint jiraEndpoint;
    Vertx vertx;
    HttpClientService httpClientService;

    @Before
    public void setUp() throws Exception {
        JsonObject conf = ConfigModelTest.getConfig1();
        ConfigManager.init(conf);
        vertx = Vertx.vertx();
        MongoDb.getInstance().init(vertx.eventBus(), "fr.openent.supportpivot");
        this.httpClientService = Mockito.spy(new HttpClientService(vertx));
        this.jiraEndpoint = Mockito.mock(JiraEndpoint.class);
    }

    @Test
    public void prepareSearchRequestTest(TestContext ctx) throws Exception {
        JsonObject data  =new JsonObject("{\"Attribution\":\"LDE\",\"custom_field\":\"id_externe\"}");
        URI result = Whitebox.invokeMethod(this.jiraEndpoint, "prepareSearchRequest", data);
        String expected = "https://jira-test.support-ent.fr/rest/api/2/search?jql=%28assignee+%3D+LDE+or+cf%5B13401%5D+is+not+EMPTY%29&fields=id,key,,updated,created,customfield_12705";
        ctx.assertEquals(result.toString(), expected);
    }

    @Test
    public void getJiraPJTest(TestContext ctx) throws Exception {
        Async async = ctx.async();
        JiraAttachment jiraAttachment = new JiraAttachment();
        jiraAttachment.setContent("content");

        PivotHttpClient pivotHttpClient = Mockito.mock(PivotHttpClient.class);
        Whitebox.setInternalState(this.jiraEndpoint, "httpClient", pivotHttpClient);
        PivotHttpClientRequest pivotHttpClientRequest = Mockito.mock(PivotHttpClientRequest.class);
        HttpClientResponseImpl httpClientResponse = Mockito.mock(HttpClientResponseImpl.class);
        Mockito.doReturn(200).when(httpClientResponse).statusCode();
        Mockito.doAnswer(invocation -> {
            Handler<Buffer> bodyHandler = invocation.getArgument(0);
            bodyHandler.handle(new BufferImpl().appendString("StringBuffer"));
            return null;
        }).when(httpClientResponse).bodyHandler(Mockito.any());

        Mockito.doReturn(pivotHttpClientRequest).when(pivotHttpClient).createGetRequest(Mockito.eq("content"));
        Mockito.doAnswer(invocation -> {
            Handler<AsyncResult<HttpClientResponse>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(httpClientResponse));
            return null;
        }).when(pivotHttpClientRequest).startRequest(Mockito.any());

        Future<String> future = Whitebox.invokeMethod(this.jiraEndpoint, "getJiraPJ", jiraAttachment);
        String expected = "U3RyaW5nQnVmZmVy";
        future.onSuccess(base64JP -> {
            ctx.assertEquals(expected, base64JP);
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void convertJiraReponseToJsonPivotTest(TestContext ctx) throws Exception {
        Async async = ctx.async();
        PowerMockito.spy(JiraEndpoint.class);
        this.jiraEndpoint = PowerMockito.mock(JiraEndpoint.class);
        PowerMockito.doReturn(Future.succeededFuture("B64URL")).when(this.jiraEndpoint, "getJiraPJ", Mockito.any());
        PowerMockito.doCallRealMethod().when(this.jiraEndpoint).convertJiraReponseToJsonPivot(Mockito.any(JiraTicket.class), Mockito.any());
        PowerMockito.doCallRealMethod().when(this.jiraEndpoint, "stringEncode", Mockito.anyString());

        String expected = "{\"id_jira\":\"FICTEST-336\",\"collectivite\":\"CRIF\",\"academie\":\"CRIF\",\"creation\":\"2022-09-07T17:38:57.960+0200\"," +
                "\"maj\":\"2022-12-06T16:01:29.309+0100\",\"demandeur\":\"PRUDON Nathalie | nom.prenom@gmail.com | null | Francois-Mauriac | 1016024A\"," +
                "\"type_demande\":\"Assistance\",\"titre\":\"[Assistance ENT 629] test\",\"uai\":\"12345678X\",\"description\":\"test\"," +
                "\"priorite\":\"Mineur\",\"modules\":[\"HDF\"],\"id_ent\":\"631\",\"commentaires\":[null,null,null,null,null,null,null,null,null]," +
                "\"statut_ent\":\"Ouvert\",\"statut_jira\":\"Ouvert\",\"date_creation\":\"07/09/2022 17:38\",\"id_externe\":\"lde_5002\"," +
                "\"statut_externe\":\"En traitement ED\",\"attribution\":\"RECTORAT\",\"pj\":[{\"nom\":\"images_LDE.png\",\"contenu\":\"B64URL\"}]}";
        this.jiraEndpoint.convertJiraReponseToJsonPivot(new JiraTicket(getJiraTicket1()), event -> {
            ctx.assertEquals(event.right().getValue().toString(), expected);
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    private JsonObject getJiraTicket1() {
        return new JsonObject("{\n" +
                "  \"expand\": \"renderedFields,names,schema,operations,editmeta,changelog,versionedRepresentations\",\n" +
                "  \"id\": \"50605\",\n" +
                "  \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605\",\n" +
                "  \"key\": \"FICTEST-336\",\n" +
                "  \"fields\": {\n" +
                "    \"issuetype\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issuetype/10301\",\n" +
                "      \"id\": \"10301\",\n" +
                "      \"description\": \"Assistance sur une fonctionnalité de l'ENT\",\n" +
                "      \"iconUrl\": \"https://jira-test.support-ent.fr/secure/viewavatar?size=xsmall&avatarId=10320&avatarType=issuetype\",\n" +
                "      \"name\": \"Assistance\",\n" +
                "      \"subtask\": false,\n" +
                "      \"avatarId\": 10320\n" +
                "    },\n" +
                "    \"timespent\": null,\n" +
                "    \"customfield_13100\": null,\n" +
                "    \"project\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/project/12700\",\n" +
                "      \"id\": \"12700\",\n" +
                "      \"key\": \"FICTEST\",\n" +
                "      \"name\": \"FICTIF-TEST\",\n" +
                "      \"projectTypeKey\": \"software\",\n" +
                "      \"avatarUrls\": {\n" +
                "        \"48x48\": \"https://jira-test.support-ent.fr/secure/projectavatar?avatarId=10324\",\n" +
                "        \"24x24\": \"https://jira-test.support-ent.fr/secure/projectavatar?size=small&avatarId=10324\",\n" +
                "        \"16x16\": \"https://jira-test.support-ent.fr/secure/projectavatar?size=xsmall&avatarId=10324\",\n" +
                "        \"32x32\": \"https://jira-test.support-ent.fr/secure/projectavatar?size=medium&avatarId=10324\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"customfield_13102\": null,\n" +
                "    \"customfield_13101\": null,\n" +
                "    \"fixVersions\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"aggregatetimespent\": null,\n" +
                "    \"customfield_13103\": null,\n" +
                "    \"resolution\": null,\n" +
                "    \"customfield_13700\": null,\n" +
                "    \"customfield_10104\": null,\n" +
                "    \"customfield_12404\": null,\n" +
                "    \"customfield_12601\": null,\n" +
                "    \"customfield_12403\": null,\n" +
                "    \"customfield_10902\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"resolutiondate\": null,\n" +
                "    \"workratio\": -1,\n" +
                "    \"lastViewed\": \"2022-12-06T15:01:25.079+0100\",\n" +
                "    \"watches\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/FICTEST-336/watchers\",\n" +
                "      \"watchCount\": 2,\n" +
                "      \"isWatching\": true\n" +
                "    },\n" +
                "    \"created\": \"2022-09-07T17:38:57.960+0200\",\n" +
                "    \"customfield_12400\": null,\n" +
                "    \"priority\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/priority/5\",\n" +
                "      \"iconUrl\": \"https://jira-test.support-ent.fr/images/icons/priorities/lowest.svg\",\n" +
                "      \"name\": \"Mineure\",\n" +
                "      \"id\": \"5\"\n" +
                "    },\n" +
                "    \"customfield_12402\": null,\n" +
                "    \"customfield_10102\": null,\n" +
                "    \"customfield_10103\": null,\n" +
                "    \"customfield_12401\": null,\n" +
                "    \"labels\": [\n" +
                "      \"HDF\"\n" +
                "    ],\n" +
                "    \"customfield_12711\": \"Ouvert\",\n" +
                "    \"customfield_13404\": \"\",\n" +
                "    \"customfield_13800\": null,\n" +
                "    \"customfield_12713\": null,\n" +
                "    \"timeestimate\": null,\n" +
                "    \"aggregatetimeoriginalestimate\": null,\n" +
                "    \"versions\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"issuelinks\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"assignee\": null,\n" +
                "    \"updated\": \"2022-12-06T16:01:29.309+0100\",\n" +
                "    \"status\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/status/10401\",\n" +
                "      \"description\": \"\",\n" +
                "      \"iconUrl\": \"https://jira-test.support-ent.fr/\",\n" +
                "      \"name\": \"En cours\",\n" +
                "      \"id\": \"10401\",\n" +
                "      \"statusCategory\": {\n" +
                "        \"self\": \"https://jira-test.support-ent.fr/rest/api/2/statuscategory/4\",\n" +
                "        \"id\": 4,\n" +
                "        \"key\": \"indeterminate\",\n" +
                "        \"colorName\": \"inprogress\",\n" +
                "        \"name\": \"En cours\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"components\": [\n" +
                "      {\n" +
                "        \"self\": \"https://jira-test.support-ent.fr/rest/api/2/component/13407\",\n" +
                "        \"id\": \"13407\",\n" +
                "        \"name\": \"Actualité\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"timeoriginalestimate\": null,\n" +
                "    \"customfield_13000\": \"{summaryBean=com.atlassian.jira.plugin.devstatus.rest.SummaryBean@d058225[summary={pullrequest=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@28bbb51e[overall=PullRequestOverallBean{stateCount=0, state='OPEN', details=PullRequestOverallDetails{openCount=0, mergedCount=0, declinedCount=0}},byInstanceType={}], build=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@7e9a301b[overall=com.atlassian.jira.plugin.devstatus.summary.beans.BuildOverallBean@bfc6993[failedBuildCount=0,successfulBuildCount=0,unknownBuildCount=0,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], review=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@7270f0a6[overall=com.atlassian.jira.plugin.devstatus.summary.beans.ReviewsOverallBean@36068dff[stateCount=0,state=<null>,dueDate=<null>,overDue=false,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], deployment-environment=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@19b369cc[overall=com.atlassian.jira.plugin.devstatus.summary.beans.DeploymentOverallBean@71eb2b98[topEnvironments=[],showProjects=false,successfulCount=0,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], repository=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@46b00c3[overall=com.atlassian.jira.plugin.devstatus.summary.beans.CommitOverallBean@6800cc4[count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], branch=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@4fff8d1[overall=com.atlassian.jira.plugin.devstatus.summary.beans.BranchOverallBean@2ce0b43d[count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}]},errors=[],configErrors=[]], devSummaryJson={\\\"cachedValue\\\":{\\\"errors\\\":[],\\\"configErrors\\\":[],\\\"summary\\\":{\\\"pullrequest\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"stateCount\\\":0,\\\"state\\\":\\\"OPEN\\\",\\\"details\\\":{\\\"openCount\\\":0,\\\"mergedCount\\\":0,\\\"declinedCount\\\":0,\\\"total\\\":0},\\\"open\\\":true},\\\"byInstanceType\\\":{}},\\\"build\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"failedBuildCount\\\":0,\\\"successfulBuildCount\\\":0,\\\"unknownBuildCount\\\":0},\\\"byInstanceType\\\":{}},\\\"review\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"stateCount\\\":0,\\\"state\\\":null,\\\"dueDate\\\":null,\\\"overDue\\\":false,\\\"completed\\\":false},\\\"byInstanceType\\\":{}},\\\"deployment-environment\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"topEnvironments\\\":[],\\\"showProjects\\\":false,\\\"successfulCount\\\":0},\\\"byInstanceType\\\":{}},\\\"repository\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null},\\\"byInstanceType\\\":{}},\\\"branch\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null},\\\"byInstanceType\\\":{}}}},\\\"isStale\\\":false}}\",\n" +
                "    \"description\": \"test\",\n" +
                "    \"customfield_13401\": \"lde_5002\",\n" +
                "    \"customfield_13400\": \"{}\",\n" +
                "    \"customfield_13403\": \"12345678X\",\n" +
                "    \"customfield_13601\": null,\n" +
                "    \"customfield_13402\": \"En traitement ED\",\n" +
                "    \"timetracking\": {\n" +
                "      \n" +
                "    },\n" +
                "    \"archiveddate\": null,\n" +
                "    \"customfield_12701\": null,\n" +
                "    \"customfield_12107\": null,\n" +
                "    \"customfield_10005\": null,\n" +
                "    \"customfield_12700\": null,\n" +
                "    \"customfield_12106\": null,\n" +
                "    \"customfield_12703\": null,\n" +
                "    \"customfield_12901\": null,\n" +
                "    \"customfield_12702\": null,\n" +
                "    \"customfield_12900\": null,\n" +
                "    \"customfield_12705\": \"07/09/2022 17:38\",\n" +
                "    \"attachment\": [\n" +
                "      {\n" +
                "        \"self\": \"https://jira-test.support-ent.fr/rest/api/2/attachment/28500\",\n" +
                "        \"id\": \"28500\",\n" +
                "        \"filename\": \"images_LDE.png\",\n" +
                "        \"author\": {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "          \"name\": \"assistanceMLN\",\n" +
                "          \"key\": \"assistancemln\",\n" +
                "          \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "          \"avatarUrls\": {\n" +
                "            \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "            \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "            \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "            \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "          },\n" +
                "          \"displayName\": \"Assistance Monlycee.net\",\n" +
                "          \"active\": true,\n" +
                "          \"timeZone\": \"Europe/Paris\"\n" +
                "        },\n" +
                "        \"created\": \"2022-09-13T12:32:58.612+0200\",\n" +
                "        \"size\": 2646,\n" +
                "        \"mimeType\": \"image/png\",\n" +
                "        \"content\": \"https://jira-test.support-ent.fr/secure/attachment/28500/images_LDE.png\",\n" +
                "        \"thumbnail\": \"https://jira-test.support-ent.fr/secure/thumbnail/28500/_thumb_28500.png\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"customfield_10009\": \"0|i05wjc:\",\n" +
                "    \"aggregatetimeestimate\": null,\n" +
                "    \"customfield_12704\": null,\n" +
                "    \"customfield_12706\": null,\n" +
                "    \"customfield_12907\": null,\n" +
                "    \"customfield_12708\": \"631\",\n" +
                "    \"summary\": \"[Assistance ENT 629] test\",\n" +
                "    \"creator\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=fictif.test.supportpivot\",\n" +
                "      \"name\": \"fictif.test.supportpivot\",\n" +
                "      \"key\": \"fictif.test.supportpivot\",\n" +
                "      \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "      \"avatarUrls\": {\n" +
                "        \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "        \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "        \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "        \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "      },\n" +
                "      \"displayName\": \"Fictif test Support Pivot\",\n" +
                "      \"active\": true,\n" +
                "      \"timeZone\": \"Europe/Paris\"\n" +
                "    },\n" +
                "    \"customfield_14000\": null,\n" +
                "    \"subtasks\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"customfield_14003\": null,\n" +
                "    \"customfield_14004\": null,\n" +
                "    \"reporter\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "      \"name\": \"assistanceMLN\",\n" +
                "      \"key\": \"assistancemln\",\n" +
                "      \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "      \"avatarUrls\": {\n" +
                "        \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "        \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "        \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "        \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "      },\n" +
                "      \"displayName\": \"Assistance Monlycee.net\",\n" +
                "      \"active\": true,\n" +
                "      \"timeZone\": \"Europe/Paris\"\n" +
                "    },\n" +
                "    \"customfield_14001\": null,\n" +
                "    \"customfield_10000\": null,\n" +
                "    \"customfield_14002\": null,\n" +
                "    \"aggregateprogress\": {\n" +
                "      \"progress\": 0,\n" +
                "      \"total\": 0\n" +
                "    },\n" +
                "    \"customfield_12103\": null,\n" +
                "    \"customfield_10001\": null,\n" +
                "    \"customfield_12102\": null,\n" +
                "    \"customfield_12105\": null,\n" +
                "    \"customfield_12104\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"customfield_10004\": null,\n" +
                "    \"customfield_11405\": \"PRUDON Nathalie | nom.prenom@gmail.com | null | Francois-Mauriac | 1016024A\",\n" +
                "    \"customfield_11603\": null,\n" +
                "    \"environment\": null,\n" +
                "    \"customfield_13901\": null,\n" +
                "    \"customfield_11406\": null,\n" +
                "    \"duedate\": null,\n" +
                "    \"progress\": {\n" +
                "      \"progress\": 0,\n" +
                "      \"total\": 0\n" +
                "    },\n" +
                "    \"comment\": {\n" +
                "      \"comments\": [\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97312\",\n" +
                "          \"id\": \"97312\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=fictif.test.supportpivot\",\n" +
                "            \"name\": \"fictif.test.supportpivot\",\n" +
                "            \"key\": \"fictif.test.supportpivot\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Fictif test Support Pivot\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 20220907153923 |\\n PRUDON Nathalie |\\n 2022-09-07 15:39:23 |\\n\\n test st\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=fictif.test.supportpivot\",\n" +
                "            \"name\": \"fictif.test.supportpivot\",\n" +
                "            \"key\": \"fictif.test.supportpivot\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Fictif test Support Pivot\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-07T17:39:25.408+0200\",\n" +
                "          \"updated\": \"2022-09-07T17:39:25.408+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97313\",\n" +
                "          \"id\": \"97313\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=fictif.test.supportpivot\",\n" +
                "            \"name\": \"fictif.test.supportpivot\",\n" +
                "            \"key\": \"fictif.test.supportpivot\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Fictif test Support Pivot\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 20220907154129 |\\n PRUDON Nathalie |\\n 2022-09-07 15:41:29 |\\n\\n testtetet\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=fictif.test.supportpivot\",\n" +
                "            \"name\": \"fictif.test.supportpivot\",\n" +
                "            \"key\": \"fictif.test.supportpivot\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Fictif test Support Pivot\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-07T17:41:30.899+0200\",\n" +
                "          \"updated\": \"2022-09-07T17:41:30.899+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97212\",\n" +
                "          \"id\": \"97212\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 785 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 2\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-13T12:32:58.067+0200\",\n" +
                "          \"updated\": \"2022-09-13T12:32:58.067+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97213\",\n" +
                "          \"id\": \"97213\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 7856 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 2\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-13T14:18:04.330+0200\",\n" +
                "          \"updated\": \"2022-09-13T14:18:04.330+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97214\",\n" +
                "          \"id\": \"97214\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 800 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 2\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-13T18:17:04.783+0200\",\n" +
                "          \"updated\": \"2022-09-13T18:17:04.783+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97316\",\n" +
                "          \"id\": \"97316\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 801 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 3\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-14T09:26:35.610+0200\",\n" +
                "          \"updated\": \"2022-09-14T09:26:35.610+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97215\",\n" +
                "          \"id\": \"97215\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 78562 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 2\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-09-14T15:11:12.239+0200\",\n" +
                "          \"updated\": \"2022-09-14T15:11:12.239+0200\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97219\",\n" +
                "          \"id\": \"97219\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 802 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 4\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-12-01T10:32:51.513+0100\",\n" +
                "          \"updated\": \"2022-12-01T10:32:51.513+0100\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97320\",\n" +
                "          \"id\": \"97320\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \" 805 |\\n Commentaires LDE |\\n Date |\\n\\n Commentaire de LDE sur la demande 50\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-12-05T09:59:50.157+0100\",\n" +
                "          \"updated\": \"2022-12-05T09:59:50.157+0100\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/50605/comment/97321\",\n" +
                "          \"id\": \"97321\",\n" +
                "          \"author\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"body\": \"Commentaire limiter\",\n" +
                "          \"updateAuthor\": {\n" +
                "            \"self\": \"https://jira-test.support-ent.fr/rest/api/2/user?username=assistanceMLN\",\n" +
                "            \"name\": \"assistanceMLN\",\n" +
                "            \"key\": \"assistancemln\",\n" +
                "            \"emailAddress\": \"valentin.peyratout@cgi.com\",\n" +
                "            \"avatarUrls\": {\n" +
                "              \"48x48\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=48\",\n" +
                "              \"24x24\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=24\",\n" +
                "              \"16x16\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=16\",\n" +
                "              \"32x32\": \"https://www.gravatar.com/avatar/ac6e0645a8b09668e8f0b71e699b2056?d=mm&s=32\"\n" +
                "            },\n" +
                "            \"displayName\": \"Assistance Monlycee.net\",\n" +
                "            \"active\": true,\n" +
                "            \"timeZone\": \"Europe/Paris\"\n" +
                "          },\n" +
                "          \"created\": \"2022-12-06T16:01:29.309+0100\",\n" +
                "          \"updated\": \"2022-12-06T16:01:29.309+0100\",\n" +
                "          \"visibility\": {\n" +
                "            \"type\": \"role\",\n" +
                "            \"value\": \"CGI / Gd ouest\"\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"maxResults\": 10,\n" +
                "      \"total\": 10,\n" +
                "      \"startAt\": 0\n" +
                "    },\n" +
                "    \"votes\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/issue/FICTEST-336/votes\",\n" +
                "      \"votes\": 0,\n" +
                "      \"hasVoted\": false\n" +
                "    },\n" +
                "    \"worklog\": {\n" +
                "      \"startAt\": 0,\n" +
                "      \"maxResults\": 20,\n" +
                "      \"total\": 0,\n" +
                "      \"worklogs\": [\n" +
                "        \n" +
                "      ]\n" +
                "    },\n" +
                "    \"archivedby\": null\n" +
                "  }\n" +
                "}");
    }
}
