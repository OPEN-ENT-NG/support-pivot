package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;

public class HttpRequestHelper {
    private HttpRequestHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Create {@link HttpRequest<Buffer>} with the connection information for the jira API
     */
    public static HttpRequest<Buffer> getJiraAuthRequest(HttpMethod method, String requestURI) {
        HttpRequest<Buffer> request = ServiceManager.getInstance().getWebClient().requestAbs(method, requestURI);
        request.basicAuthentication(ConfigManager.getInstance().getJiraLogin(), ConfigManager.getInstance().getJiraPassword());
        request.putHeader("Content-Type", "application/json");
        return request;
    }
}
