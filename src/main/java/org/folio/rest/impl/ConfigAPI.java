package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.resource.ConfigurationsResource;
import org.folio.rest.persist.MongoCRUD;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;

import com.fasterxml.jackson.databind.ObjectMapper;

@Path("configurations")
public class ConfigAPI implements ConfigurationsResource {

  private final Messages            messages          = Messages.getInstance();
  private static final ObjectMapper mapper            = new ObjectMapper();
  public static final String        CONFIG_COLLECTION = "config_data";
  public static final String        METHOD_GET        = "get";
  public static final String        METHOD_POST       = "post";
  public static final String        METHOD_PUT        = "put";
  public static final String        METHOD_DELETE     = "delete";
  private static final Logger       log               = LoggerFactory.getLogger(ConfigAPI.class);

  private static final String       LOCATION_PREFIX   = "/configurations/tables/";
  @Validate
  @Override
  public void getConfigurationsTables(String authorization, String query, String orderBy,
      Order order, int offset, int limit, String lang,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    /**
     * http://host:port/configurations/tables
     */

    try {
      System.out.println("sending... getConfigurationsTables");
      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).get(
          MongoCRUD.buildJson(Config.class.getName(), CONFIG_COLLECTION, query, orderBy, order,
            offset, limit),
            reply -> {
              try {
                Configs configs = new Configs();
                List<Config> config = (List<Config>) reply.result();
                configs.setConfigs(config);
                configs.setTotalRecords(config.size());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse.withJsonOK(
                  configs)));
              } catch (Exception e) {
                log.error(e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse
        .withPlainInternalServerError(messages.getMessage(
          lang, MessageConsts.InternalServerError))));
    }

  }

  @Validate
  @Override
  public void postConfigurationsTables(String authorization, String lang, Config entity,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    try {
      System.out.println("sending... postConfigurationsTables");

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).save(
          CONFIG_COLLECTION,
          entity,
          reply -> {
            try {
              Object ret = reply.result();
              entity.setId((String) ret);
              OutStream stream = new OutStream();
              stream.setData(entity);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse.withJsonCreated(
                LOCATION_PREFIX + ret, stream)));
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }

  @Validate
  @Override
  public void getConfigurationsTablesByTableId(String tableId, String authorization, String query,
      String orderBy, Order order, int offset, int limit, String lang,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    try {
      System.out.println("sending... getConfigurationsTablesByTableId");
      context.runOnContext(v -> {
        JsonObject q = new JsonObject();
        if (query != null) {
          q = new JsonObject(query);
        }
        q.put("_id", tableId);
        MongoCRUD.getInstance(context.owner()).get(
          MongoCRUD.buildJson(Config.class.getName(), CONFIG_COLLECTION, q, orderBy, order, offset,
            limit),
            reply -> {
              try {
                Configs configs = new Configs();
                List<Config> config = (List<Config>) reply.result();
                if(config.isEmpty()){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
                    .withPlainNotFound(tableId)));
                }
                else{
                  configs.setConfigs(config);
                  configs.setTotalRecords(config.size());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
                    .withJsonOK(configs)));
                }
              } catch (Exception e) {
                log.error(e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
    }

  }

  @Validate
  @Override
  public void deleteConfigurationsTablesByTableId(String tableId, String authorization,
      String lang, Handler<AsyncResult<Response>> asyncResultHandler, Context context)
          throws Exception {

    try {
      JsonObject q = new JsonObject();
      q.put("_id", tableId);
      System.out.println("sending... deleteConfigurationsTablesByTableId");

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).delete(
          CONFIG_COLLECTION,
          q,
          reply -> {
            try {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
                .withNoContent()));
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
    }

  }

  @Validate
  @Override
  public void putConfigurationsTablesByTableId(String tableId, String authorization, String lang,
      Configs entity, Handler<AsyncResult<Response>> asyncResultHandler, Context context)
          throws Exception {

    try {
      JsonObject q = new JsonObject();
      q.put("_id", tableId);
      System.out.println("sending... putConfigurationsTablesByTableId");

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).update(
          CONFIG_COLLECTION,
          entity,
          q,
          reply -> {
            try {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
                .withNoContent()));
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }
}
