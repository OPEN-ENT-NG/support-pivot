package fr.openent.supportpivot.model.endpoint.jira;

import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ConfigModelTest;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraServiceImpl;
import fr.openent.supportpivot.services.MongoService;
import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.net.URI;

@RunWith(VertxUnitRunner.class)
public class JiraEndpointTest {
    JiraEndpoint jiraEndpoint;
    Vertx vertx;

    @Before
    public void setUp() {
        JsonObject conf = ConfigModelTest.getConfig1();
        ConfigManager.init(conf);
        vertx = Vertx.vertx();
        MongoDb.getInstance().init(vertx.eventBus(), "fr.openent.supportpivot");

        this.jiraEndpoint = new JiraEndpoint(new HttpClientService(vertx), new JiraServiceImpl(vertx));
    }

    @Test
    public void prepareSearchRequestTest(TestContext ctx) throws Exception {
        JsonObject data  =new JsonObject("{\"Attribution\":\"LDE\",\"custom_field\":\"id_externe\"}");
        URI result = Whitebox.invokeMethod(this.jiraEndpoint, "prepareSearchRequest", data);
        String expected = "https://jira-test.support-ent.fr/rest/api/2/search?jql=%28assignee+%3D+LDE+or+cf%5B13401%5D+is+not+EMPTY%29&fields=id,key,,updated,created,customfield_12705";
        ctx.assertEquals(result.toString(), expected);
    }
}
