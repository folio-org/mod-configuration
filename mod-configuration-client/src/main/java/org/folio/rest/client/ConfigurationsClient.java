
package org.folio.rest.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class ConfigurationsClient {

    private final static String GLOBAL_PATH = "/configurations";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public ConfigurationsClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        io.vertx.core.Context context = io.vertx.core.Vertx.currentContext();
        if(context == null){
          httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
        }
        else{
          httpClient = io.vertx.core.Vertx.currentContext().owner().createHttpClient(options);
        }
    }

    public ConfigurationsClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Convenience constructor for tests ONLY!<br>Connect to localhost on 8081 as folio_demo tenant.
     *
     */
    public ConfigurationsClient() {
        this("localhost", 8081, "folio_demo", false);
    }

    /**
     * Service endpoint "/configurations/entries"+queryParams.toString()
     *
     */
    public void postEntries(String lang, org.folio.rest.jaxrs.model.Config Config, Handler<HttpClientResponse> responseHandler)
        throws Exception
    {
        StringBuilder queryParams = new StringBuilder("?");
        if(lang != null) {queryParams.append("lang="+lang);
        queryParams.append("&");}
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.persist.PostgresClient.pojo2json(Config));
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/configurations/entries"+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
        if(tenantId != null){
         request.putHeader("Authorization", tenantId);
         request.putHeader("x-okapi-tenant", tenantId);
        }
        request.putHeader("Content-Length", buffer.length()+"");
        request.setChunked(true);
        request.write(buffer);
        request.end();
    }

    /**
     * Service endpoint "/configurations/entries"+queryParams.toString()
     *
     */
    public void getEntries(String query, String orderBy, org.folio.rest.jaxrs.resource.ConfigurationsResource.Order order, int offset, int limit, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        if(query != null) {queryParams.append("query="+query);
        queryParams.append("&");}
        if(orderBy != null) {queryParams.append("orderBy="+orderBy);
        queryParams.append("&");}
        if(order != null) {queryParams.append("order="+order.toString());
        queryParams.append("&");}
        queryParams.append("offset="+offset);
        queryParams.append("&");
        queryParams.append("limit="+limit);
        queryParams.append("&");
        if(lang != null) {queryParams.append("lang="+lang);
        queryParams.append("&");}
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/configurations/entries"+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "application/json,text/plain");
        if(tenantId != null){
         request.putHeader("Authorization", tenantId);
         request.putHeader("x-okapi-tenant", tenantId);
        }
        request.end();
    }

    /**
     * Service endpoint "/configurations/entries/"+entryId+""+queryParams.toString()
     *
     */
    public void deleteEntryId(String entryId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        if(lang != null) {queryParams.append("lang="+lang);
        queryParams.append("&");}
        io.vertx.core.http.HttpClientRequest request = httpClient.delete("/configurations/entries/"+entryId+""+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "text/plain");
        if(tenantId != null){
         request.putHeader("Authorization", tenantId);
         request.putHeader("x-okapi-tenant", tenantId);
        }
        request.end();
    }

    /**
     * Service endpoint "/configurations/entries/"+entryId+""+queryParams.toString()
     *
     */
    public void getEntryId(String entryId, String query, String orderBy, org.folio.rest.jaxrs.resource.ConfigurationsResource.Order order, int offset, int limit, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        if(query != null) {queryParams.append("query="+query);
        queryParams.append("&");}
        if(orderBy != null) {queryParams.append("orderBy="+orderBy);
        queryParams.append("&");}
        if(order != null) {queryParams.append("order="+order.toString());
        queryParams.append("&");}
        queryParams.append("offset="+offset);
        queryParams.append("&");
        queryParams.append("limit="+limit);
        queryParams.append("&");
        if(lang != null) {queryParams.append("lang="+lang);
        queryParams.append("&");}
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/configurations/entries/"+entryId+""+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "application/json,text/plain");
        if(tenantId != null){
         request.putHeader("Authorization", tenantId);
         request.putHeader("x-okapi-tenant", tenantId);
        }
        request.end();
    }

    /**
     * Service endpoint "/configurations/entries/"+entryId+""+queryParams.toString()
     *
     */
    public void putEntryId(String entryId, String lang, org.folio.rest.jaxrs.model.Configs Configs, Handler<HttpClientResponse> responseHandler)
        throws Exception
    {
        StringBuilder queryParams = new StringBuilder("?");
        if(lang != null) {queryParams.append("lang="+lang);
        queryParams.append("&");}
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.persist.PostgresClient.pojo2json(Configs));
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/configurations/entries/"+entryId+""+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "text/plain");
        if(tenantId != null){
         request.putHeader("Authorization", tenantId);
         request.putHeader("x-okapi-tenant", tenantId);
        }
        request.putHeader("Content-Length", buffer.length()+"");
        request.setChunked(true);
        request.write(buffer);
        request.end();
    }

    /**
     * Close the client. Closing will close down any pooled connections. Clients should always be closed after use.
     *
     */
    public void close() {
        httpClient.close();
    }

    public String checksum() {
        return "7475e07ebd745ff40657150911037d0c";
    }

}
