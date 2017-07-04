package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Audit;
import org.folio.rest.jaxrs.model.Audits;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.resource.ConfigurationsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.JsonUtils;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
  private static final String       CONFIG_SCHEMA_NAME= "apidocs/raml/_schemas/kv_configuration.schema";
  private static String             CONFIG_SCHEMA     =  null;

  private final Messages            messages          = Messages.getInstance();

  private String idFieldName                          = "id";

  private void initCQLValidation() {
    String path = CONFIG_SCHEMA_NAME;
    try {
      CONFIG_SCHEMA = IOUtils.toString(getClass().getClassLoader().getResourceAsStream(path), "UTF-8");
    } catch (Exception e) {
      log.error("unable to load schema - " +path+ ", validation of query fields will not be active");
    }
  }

  public ConfigAPI(Vertx vertx, String tenantId) {
    if(CONFIG_SCHEMA == null){
      initCQLValidation();
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Validate
  @Override
  public void getConfigurationsEntries(String query, int offset, int limit,
      String lang,java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {


    /**
    * http://host:port/configurations/entries
    */
    context.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        CQLWrapper cql = getCQL(query,limit, offset, CONFIG_SCHEMA);

        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE, Config.class,
          new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  Configs configs = new Configs();
                  @SuppressWarnings("unchecked")
                  List<Config> config = (List<Config>) reply.result()[0];
                  configs.setConfigs(config);
                  configs.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse.withJsonOK(
                    configs)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
                    .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      }
      catch(CQLQueryValidationException e1){
        int start = e1.getMessage().indexOf("'");
        int end = e1.getMessage().lastIndexOf("'");
        String field = e1.getMessage();
        if(start != -1 && end != -1){
          field = field.substring(start+1, end);
        }
        Errors e = ValidationHelper.createValidationErrorMessage(field, "", e1.getMessage());
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
          .withPlainBadRequest(JsonUtils.entity2String(e))));
      }
      catch (Exception e) {
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
  public void getConfigurationsEntriesByEntryId(String entryId, String lang, java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    context.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsEntriesByEntryId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

        Criterion c = new Criterion(
          new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+entryId+"'"));

        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE, Config.class, c, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  @SuppressWarnings("unchecked")
                  List<Config> config = (List<Config>) reply.result()[0];
                  if(config.isEmpty()){
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .withPlainNotFound(entryId)));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .withJsonOK(config.get(0))));
                  }
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError) + " " +
                        reply.cause().getMessage())));
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

      context.runOnContext(v -> {
        System.out.println("sending... deleteConfigurationsTablesByTableId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        try {
          PostgresClient.getInstance(context.owner(), tenantId).delete(CONFIG_TABLE, entryId,
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
  public void putConfigurationsEntriesByEntryId(String entryId, String lang, Config entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      System.out.println("sending... putConfigurationsTablesByTableId");
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
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

  private CQLWrapper getCQL(String query, int limit, int offset, String schema) throws Exception {
    CQL2PgJSON cql2pgJson = null;
    if(schema != null){
      cql2pgJson = new CQL2PgJSON(CONFIG_TABLE+".jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON(CONFIG_TABLE+".jsonb");
    }
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getConfigurationsAudit(String query, int offset,
      int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    /**
    * http://host:port/configurations/tables
    */
    vertxContext.runOnContext(v -> {
      try {
        System.out.println("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        CQLWrapper cql = getCQL(query,limit, offset, CONFIG_SCHEMA);

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(AUDIT_TABLE, Audit.class,
          new String[]{"jsonb", "orig_id", "created_date", "operation"}, cql, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  Audits auditRecords = new Audits();
                  @SuppressWarnings("unchecked")
                  List<Audit> auditList = (List<Audit>) reply.result()[0];
                  auditRecords.setAudits(auditList);
                  auditRecords.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse.withJsonOK(
                    auditRecords)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse
                    .withPlainInternalServerError(messages.getMessage(
                      lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsAuditResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      }
      catch(CQLQueryValidationException e1){
        int start = e1.getMessage().indexOf("'");
        int end = e1.getMessage().lastIndexOf("'");
        String field = e1.getMessage();
        if(start != -1 && end != -1){
          field = field.substring(start+1, end);
        }
        Errors e = ValidationHelper.createValidationErrorMessage(field, "", e1.getMessage());
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsEntriesResponse
          .withPlainBadRequest(JsonUtils.entity2String(e))));
      }
      catch (Exception e) {
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
