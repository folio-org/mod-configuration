package com.sling.rest.impl;

import java.io.InputStream;
import java.util.List;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folio.rulez.Rules;
import com.sling.rest.annotations.Validate;
import com.sling.rest.jaxrs.model.Config;
import com.sling.rest.jaxrs.model.Configs;
import com.sling.rest.jaxrs.resource.ConfigurationsResource;
import com.sling.rest.persist.MongoCRUD;
import com.sling.rest.resource.utils.OutStream;
import com.sling.rest.resource.utils.RestUtils;
import com.sling.rest.tools.Messages;

@Path("apis/configurations")
public class ConfigAPI implements ConfigurationsResource {

  private final Messages            messages = Messages.getInstance();
  private static final ObjectMapper mapper   = new ObjectMapper();
  public static final String CONFIG_COLLECTION  = "config_data";
  public static final String METHOD_GET         = "get";
  public static final String METHOD_POST        = "post";
  public static final String METHOD_PUT         = "put";
  public static final String METHOD_DELETE      = "delete";

  @Validate
  @Override
  public void getConfigurationsTables(String authorization, String query, String orderBy, Order order, int offset, int limit, String lang,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    /**
     * http://host:port/apis/configurations/tables
     */
    
    try {
      System.out.println("sending... getConfigurationsTables");
      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).get(
          MongoCRUD.buildJson(Config.class.getName(), CONFIG_COLLECTION, query, orderBy, order, offset, limit),
            reply -> {
              try {
                Configs configs = new Configs();
                List<Config> config = (List<Config>)reply.result();
                configs.setConfigs(config);
                configs.setTotalRecords(config.size());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse.withJsonOK(configs)));
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse
                    .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
              }
            });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesResponse.withPlainInternalServerError(messages
          .getMessage(lang, "10001"))));
    }

  }

  @Validate
  @Override
  public void postConfigurationsTables(String authorization, String lang, Config entity, Handler<AsyncResult<Response>> asyncResultHandler,
      Context context) throws Exception {

    try {
      System.out.println("sending... postConfigurationsTables");
      JsonObject jObj = RestUtils.createMongoObject(CONFIG_COLLECTION, METHOD_POST, authorization, null, null, null, 0, 0,
          entity, null);

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).save(CONFIG_COLLECTION, entity,
            reply -> {
              try {
                Object ret = reply.result();
                OutStream stream = new OutStream();
                stream.setData(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse.withJsonCreated(stream
                    .getData().toString(), stream)));
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse
                    .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
              }
            });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesResponse.withPlainInternalServerError(messages
          .getMessage(lang, "10001"))));
    }
  }

  @Validate
  @Override
  public void getConfigurationsTablesByTableId(String tableId, String authorization, String query, String orderBy, Order order, int offset,
      int limit, String lang, Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    try {   
      System.out.println("sending... getConfigurationsTablesByTableId");
      context.runOnContext(v -> {
        JsonObject q = new JsonObject();
        if(query != null){
          q = new JsonObject(query);          
        }
        q.put("_id", tableId);
        MongoCRUD.getInstance(context.owner()).get(
          MongoCRUD.buildJson(Config.class.getName(), CONFIG_COLLECTION, q, orderBy, order, offset, limit),
            reply -> {
              try {
                Object ret = reply.result();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
                    .withJsonOK(new ObjectMapper().readValue(ret.toString(), Configs.class))));
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
              }
            });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetConfigurationsTablesByTableIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
    }

  }

  @Validate
  @Override
  public void deleteConfigurationsTablesByTableId(String tableId, String authorization, String lang,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    try {
      JsonObject q = new JsonObject();
      q.put("_id", tableId);
      System.out.println("sending... deleteConfigurationsTablesByTableId");

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner())
            .delete(CONFIG_COLLECTION, q,
                reply -> {
                  try {
                    Object ret = reply.result();
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
                        .withNoContent()));
                  } catch (Exception e) {
                    e.printStackTrace();
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
                  }
                });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteConfigurationsTablesByTableIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
    }

  }

  @Validate
  @Override
  public void putConfigurationsTablesByTableId(String tableId, String authorization, String lang, Configs entity,
      Handler<AsyncResult<Response>> asyncResultHandler, Context context) throws Exception {

    try {
      JsonObject q = new JsonObject();
      q.put("_id", tableId);
      System.out.println("sending... putConfigurationsTablesByTableId");

      context.runOnContext(v -> {
        MongoCRUD.getInstance(context.owner()).update(CONFIG_COLLECTION, entity, q,
            reply -> {
              try {
                Object ret = reply.result();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse.withNoContent()));
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
              }
            });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
    }

  }


  @Validate
  @Override
  public void postConfigurationsRules(String authorization, String lang, MimeMultipart entity,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    try {

      System.out.println("sending... postConfigurationsRules");
      int parts = entity.getCount();
      Object drool = null;
      Config conf = null;
      for (int i = 0; i < parts; i++) {
        BodyPart part = entity.getBodyPart(i);
        //check header for type - not useful right now -needs to be forwarded by the framework via the mimemultipart object (not supported yet)
        //part.getContentType().contains("text/plain");
        InputStream rule = part.getInputStream();
        if(Rules.validateRules(rule).size() ==0){
          //this is the rules
          drool = part.getContent();
        }
        else {
          //this is the config entry - load into a config object, if that fails, data passed is not correct
          conf = mapper.readValue(part.getContent().toString(), Config.class);
        }
      }
      conf.getRows().get(0).setValue(drool.toString());
      final Config fconf = conf;
      vertxContext.runOnContext(v -> {
        MongoCRUD.getInstance(vertxContext.owner()).save(CONFIG_COLLECTION, fconf,
            reply -> {
              try {
                Object ret = reply.result();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse.withNoContent()));
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
              }
            });
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutConfigurationsTablesByTableIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
    }
    
  }

  @Validate
  @Override
  public void getConfigurationsRules(String authorization, String lang, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    // TODO Auto-generated method stub
    
  }

}
