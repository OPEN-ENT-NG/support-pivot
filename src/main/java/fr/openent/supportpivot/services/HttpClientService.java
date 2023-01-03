package fr.openent.supportpivot.services;

import fr.openent.supportpivot.helpers.PivotHttpClient;
import io.vertx.core.http.HttpClientOptions;

import java.net.URI;
import java.net.URISyntaxException;

public interface HttpClientService {
    @SuppressWarnings("unused")
    PivotHttpClient getHttpClient(String hostUri) throws URISyntaxException;

    HttpClientOptions getOptions(URI uri);
}
