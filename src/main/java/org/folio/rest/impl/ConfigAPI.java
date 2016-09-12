package org.folio.rest.impl;

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
import org.folio.rulez.Rules;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.resource.ConfigurationsResource;
import org.folio.rest.persist.MongoCRUD;
import org.folio.rest.tools.utils.LogUtil;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.messages.Messages;

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

  @Override
  public void getConfigurationsTablesModuleByModuleNameByName(String module, String name, String authorization, String query,
      String orderBy, Order order, int offset, int limit, String lang, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    
    
  }

  @Override
  public void postConfigurationsTablesModuleByModuleNameByName(String name, String module, String authorization, String lang,
      Config entity, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    try {
      System.out.println("sending... postConfigurationsTablesModuleByModuleNameByName");
      vertxContext.runOnContext(v -> {
        JsonObject q = new JsonObject();
        q.put("module", module);
        q.put("name", name);
        try {
          MongoCRUD.getInstance(vertxContext.owner())
              .addToArray(CONFIG_COLLECTION, "rows", entity.getRows(), q,
                  reply -> {
                    try {
                      if(reply.failed()){
                        LogUtil.formatErrorLogMessage("ConfigAPI", "postConfigurationsTablesModuleByModuleNameByName", reply.cause().getMessage());
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                          PostConfigurationsTablesModuleByModuleNameByNameResponse
                            .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
                      }else{
                        OutStream stream = new OutStream();
                        stream.setData(entity);
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                          PostConfigurationsTablesModuleByModuleNameByNameResponse.withJsonCreated(module+"_"+name,
                            stream)));                
                      }
                    } catch (Exception e) {
                      e.printStackTrace();
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                        PostConfigurationsTablesModuleByModuleNameByNameResponse
                          .withPlainInternalServerError(messages.getMessage(lang, "10001"))));
                    }
                  });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PostConfigurationsTablesModuleByModuleNameByNameResponse.withPlainInternalServerError(messages
              .getMessage(lang, "10001"))));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsTablesModuleByModuleNameByNameResponse
        .withPlainInternalServerError(messages
          .getMessage(lang, "10001"))));
    }
    
  }

}
