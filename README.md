# mod-configuration


Copyright (C) 2016 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file ["LICENSE"](https://github.com/folio-org/mod-configuration/blob/master/LICENSE) for more information.


#### Demo configuration module based on the raml-module-builder and a set of raml and json schemas backed by a mongoDB async implementation

This project is built using the raml-module-builder, using the MongoDB async client to implement some basic configuration APIs. It is highly recommended to read the [raml-module-builder README](https://github.com/folio-org/raml-module-builder/blob/master/README.md) since there are features that the mod-configuration module inherits from the raml-module-builder framework.

The idea behind this module is to provide a sample of a configuration service. The service allows to create module configurations. Within a module there are named configurations, and within a configuration there are 1..N rows.

```sh
-> Module

    -> config 1 -> row 1

    -> config 1 -> row 2

    -> config 2 -> row 1

    -> config 2 -> row 2

    -> config 2 -> row 3

```


Can be run in both embedded mongodb mode or with a regular MongoDB server

#### Instructions:

clone / download mod-configuration then `mvn clean install`

Run:

`java -jar configuration-fat.jar -Dhttp.port=8085 embed_mongo=true`


Or run via dockerfile

Note that the embedded mongo is started on a dynamic port chosen at embedded mongo start up - refer to the log ("created embedded mongo config on port 54851")


#### documentation of the APIs can be found at:

Documentation is auto-generated from the RAML file into HTML. Once the service is started the documentation can be viewed at:
http://localhost:8085/apidocs/index.html?raml=raml/configuration/config.raml

### Examples:

Make sure to include appropriate headers as the runtime framework validates them

Authorization: aaaaa

Accept: application/json

Content-Type: application/json

see configuration schema for an object description:
https://github.com/folio-org/mod-configuration/blob/master/ramls/_schemas/kv_configuration.schema

```sh

get all tables

(GET)
http://localhost:8085/configurations/tables


add a module / config pair for the circulation module and the validation rules configuration, along with 2 rows

(POST)
http://localhost:8085/configurations/tables
{
  "module": "CIRCULATION",
  "config_name": "validation_rules",
  "updated_by": "joe",
  "update_date": "2016.06.27.10.56.03",
  "scope": {
    "institution_id" : "aaa",
    "library_id" : "vvv"
  },
  "code": "PATRON_RULE",
  "description": "for patrons",
  "default": true,
  "enabled": true,
  "value": ""
}


query for a specific module / config / row

(GET)
http://localhost:8085/configurations/tables?query={"$and":[{"module":"CIRCULATION"},{"config_name":"validation_rules"},{"code":"ABC"}]}

Notice that the query parameter 'query' is a standard mongoDB query as the configuration module is mongoDB based.

```



