package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Audit;
import org.folio.rest.jaxrs.model.Audits;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.resource.ConfigurationsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.Criteria.Order.ORDER;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

@Path("configurations")
public class ConfigAPI implements ConfigurationsResource {

  public static final String        CONFIG_TABLE      = "config_data";
  public static final String        AUDIT_TABLE       = "audit_config";

  public static final String        METHOD_GET        = "get";
  public static final String        METHOD_POST       = "post";
  public static final String        METHOD_PUT        = "put";
  public static final String        METHOD_DELETE     = "delete";
  private static final Logger       log               = LoggerFactory.getLogger(ConfigAPI.class);

  private static final String       LOCATION_PREFIX   = "/configurations/entries/";
  private final Messages            messages          = Messages.getInstance();

  @Validate
  @Override
  public void getConfigurationsEntries(String query, String orderBy,
      Order order, int offset, int limit, String lang,java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    CQLWrapper cql = getCQL(query,limit, offset);
    /**
    * http://host:port/configurations/entries
    */
    context.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE, Config.class,
          new String[]{"*"}, cql, true,
            reply -> {
              try {
                Configs configs = new Configs();
                List<Config> config = (List<Config>) reply.result()[0];
                configs.setConfigs(config);
                configs.setTotalRecords(config.size());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse.withJsonOK(
                  configs)));
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
          .withPlainInternalServerError(message)));
      }
    });
  }

  @Validate
  @Override
  public void postConfigurationsEntries(String lang, Config entity, java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    context.runOnContext(v -> {
      try {
        System.out.println("sending... postConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        PostgresClient.getInstance(context.owner(), tenantId).save(
          CONFIG_TABLE,
          entity,
          reply -> {
            try {
              if(reply.succeeded()){
                Object ret = reply.result();
                entity.setId((String) ret);
                OutStream stream = new OutStream();
                stream.setData(entity);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse.withJsonCreated(
                  LOCATION_PREFIX + ret, stream)));
              }
              else{
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });

  }

  @Validate
  @Override
  public void getConfigurationsEntriesByEntryId(String entryId, String query,
      String orderBy, Order order, int offset, int limit, String lang, java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    CQLWrapper cql = getCQL(query, limit, offset);
    context.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsTablesByTableId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        JsonObject q = new JsonObject();
        if (query != null) {
          q = new JsonObject(query);
        }
        q.put("_id", entryId);
        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE,
          Config.class, new String[]{"update_date, creation_date"}, cql, true,
            reply -> {
              try {
                Configs configs = new Configs();
                List<Config> config = (List<Config>) reply.result()[0];
                if(config.isEmpty()){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                    .withPlainNotFound(entryId)));
                }
                else{
                  configs.setConfigs(config);
                  configs.setTotalRecords(config.size());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                    .withJsonOK(configs)));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
        });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteConfigurationsEntriesByEntryId(String entryId, String lang,
      java.util.Map<String, String>okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context context)
          throws Exception {

      Config c = new Config();
      c.setId(entryId);

      context.runOnContext(v -> {
        System.out.println("sending... deleteConfigurationsTablesByTableId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        try {
          PostgresClient.getInstance(context.owner(), tenantId).delete(CONFIG_TABLE, c,
            reply -> {
              try {
                if(reply.succeeded()){
                  if(reply.result().getUpdated() == 1){
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .withNoContent()));
                  }
                  else{
                    log.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .withPlainNotFound(messages.getMessage(lang, MessageConsts.DeletedCountError,1 , reply.result().getUpdated()))));
                  }
                }
                else{
                  log.error(reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
  }

  @Validate
  @Override
  public void putConfigurationsEntriesByEntryId(String entryId, String lang, Configs entity,
      java.util.Map<String, String>okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context context)
          throws Exception {

    context.runOnContext(v -> {
      System.out.println("sending... putConfigurationsTablesByTableId");
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      try {
        PostgresClient.getInstance(context.owner(), tenantId).update(
          CONFIG_TABLE, entity, entryId,
          reply -> {
            try {
              if(reply.succeeded()){
                if(reply.result().getUpdated() == 0){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                    .withNoContent()));
                }
              }
              else{
                log.error(reply.cause().getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsEntriesByEntryIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private org.folio.rest.persist.Criteria.Order getOrder(Order order, String field) {

    if (field == null) {
      return null;
    }

    String sortOrder = org.folio.rest.persist.Criteria.Order.ASC;
    if (order.name().equals("asc")) {
      sortOrder = org.folio.rest.persist.Criteria.Order.DESC;
    }

    return new org.folio.rest.persist.Criteria.Order(field, ORDER.valueOf(sortOrder.toUpperCase()));
  }

  private Criterion getcriterion(String query, int limit, int offset, Order order, String fieldld){
    if(query == null || query.length() == 0){
      return null;
    }
    Criterion criterion = Criterion.json2Criterion(query);
    criterion.setLimit(new Limit(limit)).setOffset(new Offset(offset));
    org.folio.rest.persist.Criteria.Order or = getOrder(order, fieldld);
    if (or != null) {
      criterion.setOrder(or);
    }
    return criterion;
  }

  private CQLWrapper getCQL(String query, int limit, int offset){
    CQLWrapper cql = null;
    if(query != null){
      CQL2PgJSON cql2pgJson = new CQL2PgJSON(CONFIG_TABLE+".jsonb");
      cql = new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
    }
    return cql;
  }

  @Validate
  @Override
  public void getConfigurationsAudit(String query, String orderBy, Order order, int offset,
      int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    CQLWrapper cql = getCQL(query,limit, offset);
    /**
    * http://host:port/configurations/tables
    */
    vertxContext.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(AUDIT_TABLE, Audit.class,
          new String[]{"jsonb", "orig_id", "creation_date", "operation"}, cql, true,
            reply -> {
              try {
                Audits auditRecords = new Audits();
                List<Audit> auditList = (List<Audit>) reply.result()[0];
                auditRecords.setAudits(auditList);
                auditRecords.setTotalRecords((Integer)reply.result()[1]);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse.withJsonOK(
                  auditRecords)));
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse
          .withPlainInternalServerError(message)));
      }
    });

  }
}
