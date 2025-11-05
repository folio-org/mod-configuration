package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Audit;
import org.folio.rest.jaxrs.model.Audits;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.resource.Configurations;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

@Path("configurations")
public class ConfigAPI implements Configurations {

  public static final String        CONFIG_TABLE      = "config_data";
  public static final String        AUDIT_TABLE       = "audit_config_data";

  private static final Logger log               = LogManager.getLogger(ConfigAPI.class);

  private static final String       LOCATION_PREFIX   = "/configurations/entries/";

  private final Messages            messages          = Messages.getInstance();

  @Validate
  @Override
  public void getConfigurationsEntries(
      String query, int offset, int limit, List<String> facets,
      String lang, RoutingContext routingContext, Map<String, String>okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    PgUtil.streamGet(CONFIG_TABLE, Config.class, query, offset, limit, facets, "configs", routingContext, okapiHeaders, context);
  }

  @Deprecated
  @Validate
  @Override
  public void postConfigurationsEntries(String lang, Config entity, RoutingContext routingContext,
                                        Map<String, String>okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    // cannot use PgUtil.post because Location is not returned RMB-513
    defaultToEnabled(entity);
    try {
      log.debug("sending... postConfigurationsTables");
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      PostgresClient.getInstance(context.owner(), tenantId).save(
        CONFIG_TABLE, entity.getId(), entity, reply -> {
          try {
            if (reply.succeeded()){
              String ret = reply.result();
              entity.setId(ret);
              asyncResultHandler.handle(Future.succeededFuture(
                PostConfigurationsEntriesResponse.respond201WithApplicationJson(entity,
                  PostConfigurationsEntriesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
            } else {
              log.error(reply.cause().getMessage(), reply.cause());
              if (isNotUniqueModuleConfigAndCode(reply)) {
                asyncResultHandler.handle(Future.succeededFuture(
                  PostConfigurationsEntriesResponse
                    .respond422WithApplicationJson(uniqueModuleConfigAndCodeError(entity))));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PostConfigurationsEntriesResponse.respond500WithTextPlain(
                    messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            }
          } catch (Exception e) {
            log.error(e.getMessage(), e);
            asyncResultHandler.handle(Future.succeededFuture(PostConfigurationsEntriesResponse
              .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
          }
        });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(PostConfigurationsEntriesResponse
        .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }

  @Validate
  @Override
  public void getConfigurationsEntriesByEntryId(String entryId, String lang, Map<String, String>okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    PgUtil.getById(CONFIG_TABLE, Config.class, entryId, okapiHeaders, context,
        GetConfigurationsEntriesByEntryIdResponse.class, asyncResultHandler);
  }

  @Deprecated
  @Validate
  @Override
  public void deleteConfigurationsEntriesByEntryId(String entryId, String lang, Map<String, String>okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    PgUtil.deleteById(CONFIG_TABLE, entryId, okapiHeaders, context,
        DeleteConfigurationsEntriesByEntryIdResponse.class, asyncResultHandler);
  }

  @Deprecated
  @Validate
  @Override
  public void putConfigurationsEntriesByEntryId(
      String entryId, String lang, Config entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    defaultToEnabled(entity);
    PgUtil.put(CONFIG_TABLE, entity, entryId, okapiHeaders, context, PutConfigurationsEntriesByEntryIdResponse.class,
        asyncResultHandler);
  }

  @Validate
  @Override
  public void getConfigurationsAudit(String query, int offset,
      int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(AUDIT_TABLE, Audit.class, Audits.class, query, offset, limit, okapiHeaders, vertxContext,
        GetConfigurationsAuditResponse.class, asyncResultHandler);
  }

  private <T> boolean isNotUniqueModuleConfigAndCode(AsyncResult<T> reply) {
    if (reply == null) {
      return false;
    }

    final String message = PgExceptionUtil.badRequestMessage(reply.cause());

    if (message == null) {
      return false;
    }

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
    if (entity.getEnabled() == null) {
      entity.setEnabled(true);
    }
  }
}
