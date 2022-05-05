package fr.openent.supportpivot.services;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

@RunWith(VertxUnitRunner.class)
public class MongoServiceTest {
    private Vertx vertx;
    private MongoService mongoService;
    public String mongoCollection = "pivot";
    private static final String SOURCE = "CGI";

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        MongoDb.getInstance().init(vertx.eventBus(), "fr.openent.supportpivot");

        this.mongoService = new MongoService(mongoCollection);
    }

    @Test
    public void saveTicketTest_Should_Have_Correct_Param(TestContext ctx) throws Exception {
        Async async = ctx.async();
        String source = SOURCE;
        JsonObject expectedJsonQuery = expectedJsonPivot();
        vertx.eventBus().consumer("fr.openent.supportpivot", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("insert", body.getString("action"));
            ctx.assertEquals("pivot", body.getString("collection"));
            ctx.assertEquals(expectedJsonQuery, body.getJsonObject("document"));
            async.complete();
        });
        Whitebox.invokeMethod(mongoService, "saveTicket", source, expectedJsonQuery);
    }

    private JsonObject expectedJsonPivot() {

        return new JsonObject("{" +
                "\"id_jira\" : \"49245\"," +
                "\"collectivite\" : \"CRIF\"," +
                "\"academie\" : \"t\"," +
                "\"demandeur\" : \"CHAMBOL Monique | monique.chambol@yopmail.com | null | Francois-Mauriac | 1016024A\"," +
                "\"type_demande\" : \"Assistance\"," +
                "\"titre\" : \"[Assistance ENT 410] test\"," +
                "\"description\" : \"test\"," +
                "\"priorite\" : \"Mineur\"," +
                "\"modules\" : []," +
                "\"id_ent\" : \"410\"," +
                "\"commentaires\" : [ \"20220503131017 | CHAMBOL Monique | 2022-05-03 13:10:17 | test\", \"20220503151032 | Fictif test Support Pivot | 03/05/2022 15:10:32 | test\"]," +
                "\"statut_ent\" : \"Ouvert\"," +
                "\"statut_jira\" : \"Ouvert\"," +
                "\"date_creation\" : \"03/05/2022 15:10\"," +
                "\"attribution\" : \"RECTORAT\"," +
                "\"source\" : \"CGI\"," +
                "\"date\" : \"2022-05-04T15:05:51.370+0000\""
                + "}");
    }
}
