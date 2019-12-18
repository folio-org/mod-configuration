package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.resource.Configurations;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.facets.FacetField;
import org.folio.rest.persist.facets.FacetManager;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.vertx.core.Future.succeededFuture;

@Path("configurations")
public class ConfigAPI implements Configurations {

  public static final String        CONFIG_TABLE      = "config_data";
  public static final String        AUDIT_TABLE       = "audit_config_data";

  public static final String        METHOD_GET        = "get";
  public static final String        METHOD_POST       = "post";
  public static final String        METHOD_PUT        = "put";
  public static final String        METHOD_DELETE     = "delete";
  private static final Logger       log               = LoggerFactory.getLogger(ConfigAPI.class);

  private static final String       LOCATION_PREFIX   = "/configurations/entries/";

  private final Messages            messages          = Messages.getInstance();

  @Validate
  @Override
  public void getConfigurationsEntries(String query, int offset, int limit, List<String> facets,
      String lang,java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) {


    /**
    * http://host:port/configurations/entries
    */
    context.runOnContext(v -> {
      try {
        log.debug("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        CQLWrapper cql = getCQL(CONFIG_TABLE, query,limit, offset);

        List<FacetField> facetList = FacetManager.convertFacetStrings2FacetFields(facets, "jsonb");

        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE, Config.class,
          new String[]{"*"}, cql, true, true, facetList,
            reply -> {
              try {
                if(reply.succeeded()){
                  Configs configs = new Configs();
                  List<Config> config = reply.result().getResults();
                  configs.setConfigs(config);
                  configs.setResultInfo(reply.result().getResultInfo());
                  configs.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesResponse.respond200WithApplicationJson(
                    configs)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesResponse
                  .respond500WithTextPlain(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      }
      catch(CQLQueryValidationException e) {
        handleCqlException(asyncResultHandler, e,
          GetConfigurationsEntriesResponse::respond422WithApplicationJson);      
      }
      catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesResponse
          .respond500WithTextPlain(messages.getMessage(
            lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void postConfigurationsEntries(String lang, Config entity, java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    defaultToEnabled(entity);

    context.runOnContext(v -> {
      try {
        log.debug("sending... postConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        PostgresClient.getInstance(context.owner(), tenantId).save(
          CONFIG_TABLE,
          entity.getId(),
          entity,
          reply -> {
            try {
              if(reply.succeeded()){
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(succeededFuture(
                  PostConfigurationsEntriesResponse.respond201WithApplicationJson(entity, 
                    PostConfigurationsEntriesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              }
              else {
                log.error(reply.cause().getMessage(), reply.cause());

                if(isNotUniqueModuleConfigAndCode(reply)) {
                  asyncResultHandler.handle(succeededFuture(
                    PostConfigurationsEntriesResponse
                      .respond422WithApplicationJson(uniqueModuleConfigAndCodeError(entity))));
                }
                else {
                  asyncResultHandler.handle(succeededFuture(
                    PostConfigurationsEntriesResponse.respond500WithTextPlain(
                      messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(succeededFuture(PostConfigurationsEntriesResponse
                .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(succeededFuture(PostConfigurationsEntriesResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });

  }

  @Validate
  @Override
  public void getConfigurationsEntriesByEntryId(String entryId, String lang, java.util.Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    context.runOnContext(v -> {
      try {
        log.debug("sending... getConfigurationsEntriesByEntryId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

        Criterion c = new Criterion(
          new Criteria().addField("id").setJSONB(false).setOperation("=").setVal(entryId));

        PostgresClient.getInstance(context.owner(), tenantId).get(CONFIG_TABLE, Config.class, c, false,
            reply -> {
              try {
                if(reply.succeeded()){
                  List<Config> config = reply.result().getResults();
                  if(config.isEmpty()){
                    asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .respond404WithTextPlain(entryId)));
                  }
                  else{
                    asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .respond200WithApplicationJson(config.get(0))));
                  }
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  if(isInvalidUUID(reply.cause().getMessage())){
                    asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .respond404WithTextPlain(entryId)));
                  }
                  else{
                    asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError) + " " +
                          reply.cause().getMessage())));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
        });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(succeededFuture(GetConfigurationsEntriesByEntryIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteConfigurationsEntriesByEntryId(String entryId, String lang,
      java.util.Map<String, String>okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

      context.runOnContext(v -> {
        log.debug("sending... deleteConfigurationsTablesByTableId");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        try {
          PostgresClient.getInstance(context.owner(), tenantId).delete(CONFIG_TABLE, entryId,
            reply -> {
              try {
                if(reply.succeeded()){
                  if(reply.result().getUpdated() == 1){
                    asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .respond204()));
                  }
                  else{
                    log.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                    asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.DeletedCountError,1 , reply.result().getUpdated()))));
                  }
                }
                else{
                  log.error(reply.cause());
                  if(isInvalidUUID(reply.cause().getMessage())){
                    asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .respond404WithTextPlain(entryId)));
                  }
                  else{
                    asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(succeededFuture(DeleteConfigurationsEntriesByEntryIdResponse
            .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
  }

  @Validate
  @Override
  public void putConfigurationsEntriesByEntryId(String entryId, String lang, Config entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    defaultToEnabled(entity);

    vertxContext.runOnContext(v -> {
      log.debug("sending... putConfigurationsTablesByTableId");
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
          CONFIG_TABLE, entity, entryId,
          reply -> {
            try {
              if(reply.succeeded()) {
                if(reply.result().getUpdated() == 0) {
                  asyncResultHandler.handle(succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                    .respond404WithTextPlain(entity.getId())));
                }
                else{
                  asyncResultHandler.handle(succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                    .respond204()));
                }
              }
              else {
                log.error(reply.cause().getMessage(), reply.cause());

                if(isNotUniqueModuleConfigAndCode(reply)) {
                  asyncResultHandler.handle(succeededFuture(
                    PutConfigurationsEntriesByEntryIdResponse
                      .respond422WithApplicationJson(uniqueModuleConfigAndCodeError(entity))));
                }
                else {
                  asyncResultHandler.handle(succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                    .respond204()));
                }
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(succeededFuture(PutConfigurationsEntriesByEntryIdResponse
                .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(succeededFuture(PutConfigurationsEntriesByEntryIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String table, String query, int limit, int offset)
    throws FieldException {

    final CQL2PgJSON cql2pgJson;

    cql2pgJson = new CQL2PgJSON(table+".jsonb");

    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getConfigurationsAudit(String query, int offset,
      int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    /**
    * http://host:port/configurations/tables
    */
    vertxContext.runOnContext(v -> {
      try {
        log.debug("sending... getConfigurationsTables");
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        CQLWrapper cql = getCQL(AUDIT_TABLE, query,limit, offset);

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(AUDIT_TABLE, Audit.class,
          new String[]{"jsonb", "id"}, cql, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  Audits auditRecords = new Audits();
                  List<Audit> auditList = reply.result().getResults();
                  auditRecords.setAudits(auditList);
                  auditRecords.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(succeededFuture(GetConfigurationsAuditResponse.respond200WithApplicationJson(
                    auditRecords)));
                }
                 else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(succeededFuture(GetConfigurationsAuditResponse
                    .respond500WithTextPlain(messages.getMessage(
                      lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(succeededFuture(GetConfigurationsAuditResponse
                  .respond500WithTextPlain(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      }
      catch(CQLQueryValidationException e) {
        handleCqlException(asyncResultHandler, e,
          GetConfigurationsAuditResponse::respond422WithApplicationJson);
      }
      catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(succeededFuture(GetConfigurationsAuditResponse
          .respond500WithTextPlain(message)));
      }
    });

  }

  private boolean isInvalidUUID(String errorMessage){
    /*postgres v10*//*postgres v9.6*/
    return errorMessage != null &&
      (errorMessage.contains("invalid input syntax for type uuid") /*postgres v10*/ ||
        errorMessage.contains("invalid input syntax for uuid") /*postgres v9.6*/);
  }

  private <T> boolean isNotUniqueModuleConfigAndCode(AsyncResult<T> reply) {
    if(reply == null) {
      return false;
    }

    final String message = PgExceptionUtil.badRequestMessage(reply.cause());

    if(message == null) {
      return false;
    }

    //TODO: discriminate better the different unique constraints
    return message.contains("config_data_module_configname_code_idx_unique")
      || message.contains("config_data_module_configname_idx_unique")
      || message.contains("config_data_module_configname_code_userid_idx_unique")
      || message.contains("config_data_module_configname_userid_idx_unique");
  }

  private Errors uniqueModuleConfigAndCodeError(Config entity) {
    final Error error = new Error()
      .withMessage("Cannot have more than one tenant or user record with the same module, config name and code")
      .withAdditionalProperty("module", entity.getModule())
      .withAdditionalProperty("configName", entity.getConfigName())
      .withAdditionalProperty("code", entity.getCode())
      .withAdditionalProperty("userId", entity.getUserId());

    final List<Error> errorList = new ArrayList<>();
    errorList.add(error);

    final Errors errors = new Errors();
    errors.setErrors(errorList);

    return errors;
  }

  private void defaultToEnabled(Config entity) {
    if(entity.getEnabled() == null) {
      entity.setEnabled(true);
    }
  }

  private void handleCqlException(
    Handler<AsyncResult<Response>> asyncResultHandler,
    CQLQueryValidationException exception,
    Function<Errors, Response> responseCreator) {

    log.error(exception.getMessage(), exception);

    int start = exception.getMessage().indexOf('\'');
    int end = exception.getMessage().lastIndexOf('\'');

    String field = exception.getMessage();

    if(start != -1 && end != -1){
      field = field.substring(start+1, end);
    }

    Errors e = ValidationHelper.createValidationErrorMessage(field, "", exception.getMessage());

    asyncResultHandler.handle(succeededFuture(responseCreator.apply(e)));
  }
}
