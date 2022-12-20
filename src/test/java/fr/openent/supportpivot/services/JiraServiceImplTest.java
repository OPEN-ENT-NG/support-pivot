package fr.openent.supportpivot.services;

import fr.openent.supportpivot.helpers.DateHelper;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModelTest;
import fr.openent.supportpivot.model.jira.*;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.impl.HttpClientRequestImpl;
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

import java.util.Arrays;
import java.util.List;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({PivotTicket.class, JiraServiceImpl.class, DateHelper.class}) //Prepare the static class you want to test
public class JiraServiceImplTest {
    private JiraServiceImpl jiraService;
    private Vertx vertx;
    private HttpClient httpClient;

    @Before
    public void before(TestContext ctx) throws Exception {
        this.vertx = Mockito.spy(Vertx.vertx());
        ConfigManager.init(ConfigModelTest.getConfig1());
        PowerMockito.spy(JiraServiceImpl.class);
        this.httpClient = Mockito.mock(HttpClient.class);
        Mockito.doReturn(this.httpClient).when(this.vertx).createHttpClient(Mockito.any());
        this.jiraService = PowerMockito.spy(new JiraServiceImpl(vertx));
    }

    @Test
    public void sendToJIRATest(TestContext ctx) throws Exception {
        Async async = ctx.async(3);
        PivotTicket pivotTicket = PowerMockito.spy(new PivotTicket());
        PowerMockito.doReturn(Future.succeededFuture(pivotTicket)).when(this.jiraService, "updateJiraTicket", pivotTicket);
        PowerMockito.doReturn(Future.succeededFuture(pivotTicket)).when(this.jiraService, "createJiraTicket", pivotTicket);
        //ID_EXTERNAL is mandatory
        this.jiraService.sendToJIRA(pivotTicket).onFailure(error -> async.countDown());

        pivotTicket.setIdExterne("idExternal");
        this.jiraService.sendToJIRA(pivotTicket).onSuccess(result -> {
            ctx.assertEquals(result, pivotTicket);
            async.countDown();
        });
        PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("createJiraTicket", pivotTicket);
        PowerMockito.verifyPrivate(this.jiraService, Mockito.times(0)).invoke("updateJiraTicket", pivotTicket);

        pivotTicket.setIdJira("idJira");
        this.jiraService.sendToJIRA(pivotTicket).onSuccess(result -> {
            ctx.assertEquals(result, pivotTicket);
            async.countDown();
        });
        PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("createJiraTicket", pivotTicket);
        PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("updateJiraTicket", pivotTicket);
    }

    @Test
    public void createJiraTicketTest(TestContext ctx) throws Exception {
        Async async = ctx.async();
        HttpClientResponseImpl httpClientResponse = Mockito.mock(HttpClientResponseImpl.class);
        Mockito.doReturn(201).when(httpClientResponse).statusCode();
        Mockito.doAnswer(invocation -> {
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(new BufferImpl().appendString(getJiraTicket()));
            return null;
        }).when(httpClientResponse).bodyHandler(Mockito.any());

        HttpClientRequestImpl httpClientRequest = Mockito.mock(HttpClientRequestImpl.class);
        Mockito.doReturn(httpClientRequest).when(httpClientRequest).setChunked(Mockito.anyBoolean());

        Mockito.doAnswer(invocation -> {
            Handler<HttpClientResponse> handler = invocation.getArgument(1);
            handler.handle(httpClientResponse);
            return httpClientRequest;
        }).when(this.httpClient).post(Mockito.anyString(), Mockito.any(Handler.class));

        PowerMockito.doReturn(Future.succeededFuture()).when(this.jiraService, "updateComments", Mockito.any(), Mockito.any());
        PowerMockito.doReturn(Future.succeededFuture()).when(this.jiraService, "sendMultipleJiraPJ", Mockito.any(), Mockito.any());
        PowerMockito.doReturn(new JsonObject()).when(this.jiraService, "prepareTicketForCreation", Mockito.any());
        PowerMockito.doNothing().when(this.jiraService, "terminateRequest", Mockito.any());

        PivotTicket pivotTicket = PowerMockito.spy(new PivotTicket());

        Future<PivotTicket> future = Whitebox.invokeMethod(this.jiraService, "createJiraTicket", pivotTicket);

        future.onSuccess(pivotTicketResult -> {
            ctx.assertEquals(pivotTicketResult.getIdJira(), "50605");
            ctx.assertEquals(pivotTicketResult.getStatutJira(), "Nouveau");
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void prepareTicketForCreationTest(TestContext ctx) {
        PivotTicket pivotTicket = PowerMockito.spy(new PivotTicket());
        pivotTicket.setDateCreation("2000-01-05T08:30:00.000");
        pivotTicket.setModule(Arrays.asList("/pages"));
        pivotTicket.setTypeDemande("Highest");
        pivotTicket.setPriorite("Bloquant");
        pivotTicket.setStatutEnt("Resolu");
        pivotTicket.setIdEnt("idEnt");
        pivotTicket.setTitre("Titre");
        pivotTicket.setDescription("Description");
        pivotTicket.setDateResolutionEnt("2000-01-15T08:30:00.000");
        pivotTicket.setDemandeur("Demandeur");

        String expected = "{\"fields\":{\"project\":{\"key\":\"FICTEST\"},\"summary\":\"[Assistance ENT idEnt] Titre\"," +
                "\"description\":\"Description\",\"issuetype\":{\"name\":\"Highest\"},\"customfield_12708\":\"idEnt\"," +
                "\"customfield_12711\":\"3\",\"customfield_12705\":\"05/01/2000 09:30\",\"customfield_12706\":\"2000-01-15T08:30:00.000\"," +
                "\"reporter\":{\"name\":\"assistanceMLN\"},\"customfield_11405\":\"Demandeur\",\"priority\":{\"name\":\"Highest\"}," +
                "\"components\":[{\"name\":\"Pages\"}]}}";
        ctx.assertEquals(this.jiraService.prepareTicketForCreation(pivotTicket).toString(), expected);
    }

    @Test
    public void updateCommentsTest(TestContext ctx) throws Exception {
        Async async = ctx.async();
        PowerMockito.doReturn(Future.succeededFuture()).when(this.jiraService, "sendJiraComment", Mockito.any(), Mockito.any());


        List<String> newComments = Arrays.asList("comment1", "comment2", "comment3");
        JiraTicket jiraTicket = new JiraTicket(new JsonObject());
        jiraTicket.setId("5245");
        Future<Void> future = Whitebox.invokeMethod(this.jiraService, "updateComments", newComments, jiraTicket);

        future.onSuccess(event -> {
            try {
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(3)).invoke("sendJiraComment", Mockito.eq("5245"), Mockito.any());
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraComment", Mockito.eq("5245"), Mockito.eq("comment1"));
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraComment", Mockito.eq("5245"), Mockito.eq("comment2"));
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraComment", Mockito.eq("5245"), Mockito.eq("comment3"));
            } catch (Exception e) {
                ctx.fail(e);
            }
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void sendJiraCommentTest(TestContext ctx) throws Exception {
        Async async = ctx.async();
        HttpClientResponseImpl httpClientResponse = Mockito.mock(HttpClientResponseImpl.class);
        Mockito.doReturn(201).when(httpClientResponse).statusCode();

        HttpClientRequestImpl httpClientRequest = Mockito.mock(HttpClientRequestImpl.class);
        Mockito.doReturn(httpClientRequest).when(httpClientRequest).setChunked(Mockito.anyBoolean());

        Mockito.doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            ctx.assertTrue(url.contains("5023/comment"));
            Handler<HttpClientResponse> handler = invocation.getArgument(1);
            handler.handle(httpClientResponse);
            return httpClientRequest;
        }).when(this.httpClient).post(Mockito.anyString(), Mockito.any(Handler.class));

        PowerMockito.doNothing().when(this.jiraService, "terminateRequest", Mockito.any());

        Future<Void> future = Whitebox.invokeMethod(this.jiraService, "sendJiraComment", "5023", "comment");

        future.onSuccess(event -> {
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void sendMultipleJiraPJTest(TestContext ctx) throws Exception {
        Async async = ctx.async();

        PivotPJ pivotPJ1 = new PivotPJ(new JsonObject()).setNom("Nom1").setContenu("contenu1");
        PivotPJ pivotPJ2 = new PivotPJ(new JsonObject()).setNom("Nom2").setContenu("contenu2");
        PivotPJ pivotPJ3 = new PivotPJ(new JsonObject()).setNom("Nom3").setContenu("contenu3");

        List<PivotPJ> pivotPJList = Arrays.asList(pivotPJ1, pivotPJ2, pivotPJ3);
        Future<Void> future = Whitebox.invokeMethod(this.jiraService, "sendMultipleJiraPJ", "5023", pivotPJList);

        future.onSuccess(event -> {
            try {
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(3)).invoke("sendJiraPJ", Mockito.eq("5023"), Mockito.any());
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraPJ", Mockito.eq("5023"), Mockito.eq(pivotPJ1));
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraPJ", Mockito.eq("5023"), Mockito.eq(pivotPJ2));
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraPJ", Mockito.eq("5023"), Mockito.eq(pivotPJ3));
            } catch (Exception e) {
                ctx.fail(e);
            }
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void sendJiraPJTest(TestContext ctx) throws Exception {
        Async async = ctx.async();

        HttpClientResponseImpl httpClientResponse = Mockito.mock(HttpClientResponseImpl.class);
        Mockito.doReturn(200).when(httpClientResponse).statusCode();

        HttpClientRequestImpl httpClientRequest = Mockito.mock(HttpClientRequestImpl.class);
        Mockito.doReturn(httpClientRequest).when(httpClientRequest).setChunked(Mockito.anyBoolean());
        Mockito.doReturn(httpClientRequest).when(httpClientRequest).putHeader(Mockito.anyString(), Mockito.anyString());

        Mockito.doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            ctx.assertTrue(url.contains("5024/attachments"));
            Handler<HttpClientResponse> handler = invocation.getArgument(1);
            handler.handle(httpClientResponse);
            return httpClientRequest;
        }).when(this.httpClient).post(Mockito.anyString(), Mockito.any(Handler.class));

        PowerMockito.doNothing().when(this.jiraService, "terminateRequest", Mockito.any());

        PivotPJ pivotPJ = new PivotPJ(new JsonObject());
        pivotPJ.setNom("nom").setContenu("contenu");
        Future<Void> future = Whitebox.invokeMethod(this.jiraService, "sendJiraPJ", "5024", pivotPJ);

        future.onSuccess(event -> {
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void updateJiraTicketTest(TestContext ctx) throws Exception {
        Async async = ctx.async();

        HttpClientResponseImpl httpClientResponse = Mockito.mock(HttpClientResponseImpl.class);
        Mockito.doReturn(200, 204).when(httpClientResponse).statusCode();
        Mockito.doAnswer(invocation -> {
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(new BufferImpl().appendString(getJiraTicket()));
            return null;
        }).when(httpClientResponse).bodyHandler(Mockito.any());

        HttpClientRequestImpl httpClientRequest = Mockito.mock(HttpClientRequestImpl.class);
        Mockito.doReturn(httpClientRequest).when(httpClientRequest).setChunked(Mockito.anyBoolean());

        Mockito.doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            ctx.assertTrue(url.contains("5025"));
            Handler<HttpClientResponse> handler = invocation.getArgument(1);
            handler.handle(httpClientResponse);
            return httpClientRequest;
        }).when(this.httpClient).get(Mockito.anyString(), Mockito.any(Handler.class));

        Mockito.doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            ctx.assertTrue(url.contains("5025"));
            Handler<HttpClientResponse> handler = invocation.getArgument(1);
            handler.handle(httpClientResponse);
            return httpClientRequest;
        }).when(this.httpClient).put(Mockito.anyString(), Mockito.any(Handler.class));


        PivotTicket pivotTicket = new PivotTicket();
        pivotTicket.setIdJira("5025");

        PowerMockito.doReturn(Arrays.asList("comment1", "comment2")).when(this.jiraService, "getNewComments", Mockito.eq(pivotTicket), Mockito.any(JiraTicket.class));
        PowerMockito.doReturn(Arrays.asList()).when(this.jiraService, "getNewPJs", Mockito.eq(pivotTicket), Mockito.any(JiraTicket.class));
        PowerMockito.doReturn(Future.succeededFuture()).when(this.jiraService, "sendJiraComment", Mockito.any(), Mockito.any());
        PowerMockito.doReturn(Future.succeededFuture()).when(this.jiraService, "sendMultipleJiraPJ", Mockito.any(), Mockito.any());
        PowerMockito.doNothing().when(this.jiraService, "terminateRequest", Mockito.any());

        Future<PivotTicket> future = Whitebox.invokeMethod(this.jiraService, "updateJiraTicket", pivotTicket);

        future.onSuccess(event -> {
            try {
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(2)).invoke("sendJiraComment", Mockito.eq("50605"), Mockito.any());
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraComment", Mockito.eq("50605"), Mockito.eq("comment1"));
                PowerMockito.verifyPrivate(this.jiraService, Mockito.times(1)).invoke("sendJiraComment", Mockito.eq("50605"), Mockito.eq("comment2"));
            } catch (Exception e) {
                ctx.fail(e);
            }
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void ticketPrepareForUpdateTest(TestContext ctx) throws Exception {
        PivotTicket pivotTicket = new PivotTicket();
        pivotTicket.setIdEnt("idEnt");
        pivotTicket.setIdExterne("idExterne");
        pivotTicket.setStatutEnt("Resolu");
        pivotTicket.setStatutExterne("statusExterne");
        pivotTicket.setDateResolutionEnt("2000-01-15T08:30:00.000");
        pivotTicket.setDescription("Description");
        pivotTicket.setTitre("Titre");
        pivotTicket.setDemandeur("Demander");
        JsonObject result = Whitebox.invokeMethod(this.jiraService, "ticketPrepareForUpdate", pivotTicket);

        String expected = "{\"fields\":{\"customfield_12708\":\"idEnt\",\"customfield_13401\":\"idExterne\",\"customfield_12711\":\"3\"," +
                "\"customfield_13402\":\"statusExterne\",\"customfield_12706\":\"2000-01-15T08:30:00.000\",\"description\":\"Description\"," +
                "\"summary\":\"[Assistance ENT idEnt] Titre\",\"customfield_11405\":\"Demander\"}}";
        ctx.assertEquals(result.toString(), expected);
    }

    @Test
    public void getNewCommentsTest(TestContext ctx) throws Exception {
        PivotTicket pivotTicket = new PivotTicket();
        JiraTicket jiraTicket = new JiraTicket();
        pivotTicket.setCommentaires(Arrays.asList("id1 | owner1 | 2000-10-20 | Commentaire | with pipe", "id2 | owner2 | 2000-10-22 | Commentaire 2"));
        JiraComment jiraComment = new JiraComment().setCreated("").setBody("id2 | owner2 | 2000-10-22 | Commentaire 2");
        jiraTicket.setFields(new JiraFields().setComment(new JiraFieldComment().setComments(Arrays.asList(jiraComment))));
        List<String> newComments = Whitebox.invokeMethod(this.jiraService, "getNewComments", pivotTicket, jiraTicket);
        ctx.assertEquals(newComments.size(), 1);
        ctx.assertEquals(newComments.get(0), "id1 | owner1 | 2000-10-20 | Commentaire | with pipe");
    }

    @Test
    public void getNewPJsTest(TestContext ctx) throws Exception {
        PivotTicket pivotTicket = new PivotTicket();
        JiraTicket jiraTicket = new JiraTicket();
        PivotPJ pivotPJ1 = new PivotPJ().setNom("nom1").setContenu("contenu1");
        PivotPJ pivotPJ2 = new PivotPJ().setNom("nom2").setContenu("contenu2");
        pivotTicket.setPj(Arrays.asList(pivotPJ1, pivotPJ2));
        jiraTicket.setFields(new JiraFields().setAttachment(Arrays.asList(new JiraAttachment().setFilename("nom2"))));
        List<PivotPJ> newComments = Whitebox.invokeMethod(this.jiraService, "getNewPJs", pivotTicket, jiraTicket);
    }

    public String getJiraTicket() {
        return "{\n" +
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
                "    \"resolution\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/resolution/10000\",\n" +
                "      \"id\": \"10000\",\n" +
                "      \"description\": \"Cette demande est terminée\",\n" +
                "      \"name\": \"Terminé\"\n" +
                "    },\n" +
                "    \"customfield_13700\": null,\n" +
                "    \"customfield_10104\": null,\n" +
                "    \"customfield_12404\": null,\n" +
                "    \"customfield_12601\": null,\n" +
                "    \"customfield_12403\": null,\n" +
                "    \"customfield_10902\": [\n" +
                "      \n" +
                "    ],\n" +
                "    \"resolutiondate\": \"2022-12-13T17:46:41.726+0100\",\n" +
                "    \"workratio\": -1,\n" +
                "    \"lastViewed\": \"2022-12-14T18:44:21.446+0100\",\n" +
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
                "    \"updated\": \"2022-12-13T17:46:41.731+0100\",\n" +
                "    \"status\": {\n" +
                "      \"self\": \"https://jira-test.support-ent.fr/rest/api/2/status/10402\",\n" +
                "      \"description\": \"\",\n" +
                "      \"iconUrl\": \"https://jira-test.support-ent.fr/\",\n" +
                "      \"name\": \"Fini\",\n" +
                "      \"id\": \"10402\",\n" +
                "      \"statusCategory\": {\n" +
                "        \"self\": \"https://jira-test.support-ent.fr/rest/api/2/statuscategory/3\",\n" +
                "        \"id\": 3,\n" +
                "        \"key\": \"done\",\n" +
                "        \"colorName\": \"success\",\n" +
                "        \"name\": \"Terminé\"\n" +
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
                "    \"customfield_13000\": \"{summaryBean=com.atlassian.jira.plugin.devstatus.rest.SummaryBean@2b85696b[summary={pullrequest=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@7a08a08b[overall=PullRequestOverallBean{stateCount=0, state='OPEN', details=PullRequestOverallDetails{openCount=0, mergedCount=0, declinedCount=0}},byInstanceType={}], build=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@60a76e11[overall=com.atlassian.jira.plugin.devstatus.summary.beans.BuildOverallBean@5f148676[failedBuildCount=0,successfulBuildCount=0,unknownBuildCount=0,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], review=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@4bfa855c[overall=com.atlassian.jira.plugin.devstatus.summary.beans.ReviewsOverallBean@a1e20bf[stateCount=0,state=<null>,dueDate=<null>,overDue=false,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], deployment-environment=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@5112000b[overall=com.atlassian.jira.plugin.devstatus.summary.beans.DeploymentOverallBean@2ace1b69[topEnvironments=[],showProjects=false,successfulCount=0,count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], repository=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@3581bb09[overall=com.atlassian.jira.plugin.devstatus.summary.beans.CommitOverallBean@4ee17ede[count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}], branch=com.atlassian.jira.plugin.devstatus.rest.SummaryItemBean@f323d51[overall=com.atlassian.jira.plugin.devstatus.summary.beans.BranchOverallBean@55fd6a8d[count=0,lastUpdated=<null>,lastUpdatedTimestamp=<null>],byInstanceType={}]},errors=[],configErrors=[]], devSummaryJson={\\\"cachedValue\\\":{\\\"errors\\\":[],\\\"configErrors\\\":[],\\\"summary\\\":{\\\"pullrequest\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"stateCount\\\":0,\\\"state\\\":\\\"OPEN\\\",\\\"details\\\":{\\\"openCount\\\":0,\\\"mergedCount\\\":0,\\\"declinedCount\\\":0,\\\"total\\\":0},\\\"open\\\":true},\\\"byInstanceType\\\":{}},\\\"build\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"failedBuildCount\\\":0,\\\"successfulBuildCount\\\":0,\\\"unknownBuildCount\\\":0},\\\"byInstanceType\\\":{}},\\\"review\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"stateCount\\\":0,\\\"state\\\":null,\\\"dueDate\\\":null,\\\"overDue\\\":false,\\\"completed\\\":false},\\\"byInstanceType\\\":{}},\\\"deployment-environment\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null,\\\"topEnvironments\\\":[],\\\"showProjects\\\":false,\\\"successfulCount\\\":0},\\\"byInstanceType\\\":{}},\\\"repository\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null},\\\"byInstanceType\\\":{}},\\\"branch\\\":{\\\"overall\\\":{\\\"count\\\":0,\\\"lastUpdated\\\":null},\\\"byInstanceType\\\":{}}}},\\\"isStale\\\":false}}\",\n" +
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
                "    \"customfield_11405\": \"PRUDON Nathalie | nom.prenom@gmail.com | null | François-Mauriac | 1016024A\",\n" +
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
                "}";
    }
}
