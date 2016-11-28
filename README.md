# mod-configuration


Copyright (C) 2016 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file ["LICENSE"](https://github.com/folio-org/mod-configuration/blob/master/LICENSE) for more information.


#### Demo configuration module based on the raml-module-builder and a set of raml and json schemas backed by a mongoDB async implementation

This project is built using the raml-module-builder, using the MongoDB async client to implement some basic configuration APIs. It is highly recommended to read the [raml-module-builder README](https://github.com/folio-org/raml-module-builder/blob/master/README.md) since there are features that the mod-configuration module inherits from the raml-module-builder framework.

The idea behind this module is to provide a type of centralized configuration service. The service allows for the creation of module configurations. Within a module there are named configurations, and within a configuration there are 1..N 'rows'.

```sh
-> Module

    -> config 1 -> row 1

    -> config 1 -> row 2

    -> config 2 -> row 1

    -> config 2 -> row 2

    -> config 2 -> row 3

```

This would in turn look something like:

Module| config_name | updated_by | update_date | scope | default | enabled | code | value | desc
------------ | -------------  | -------------  | -------------  | -------------  | -------------  | -------------  | -------------  | -------------  | -------------
 |  |
CIRCULATION| import.uploads.files | Joe | 1234567890 | 88 | false | true | path_2_file | PENDING | file to import
CIRCULATION| patron.drools | Joe | 1234567890 | 88 | false | true | rule_name1 | base64enc_drools_file| rule file
CIRCULATION| patron.drools | Joe | 1234567890 | 88 | false | true | rule_name2 | base64enc_drools_file| rule file

see configuration schema for an object description:
https://github.com/folio-org/mod-configuration/blob/master/ramls/_schemas/kv_configuration.schema

### Instructions

clone / download mod-configuration then `mvn clean install`

Run:

`java -jar target/configuration-fat.jar -Dhttp.port=8085 embed_mongo=true`


Or run via Dockerfile

The Configuration service can be run in both embedded mongodb mode or with a regular MongoDB server

Note that the embedded mongo is started on a dynamic port chosen at embedded mongo start up - refer to the log ("created embedded mongo config on port 54851")

### Documentation of the Service's APIs

Documentation is auto-generated from the RAML file into HTML. 
After the service is started, the documentation can be viewed at:

http://localhost:8085/apidocs/index.html?raml=raml/configuration/config.raml

### Examples

Make sure to include appropriate headers as the runtime framework validates them.


`Accept: application/json`

`Content-Type: application/json`


```sh

Query for all tables:
(GET)
http://localhost:8085/configurations/tables


Query for a specific module / config / row:
(GET)
http://localhost:8085/configurations/tables?query={"$and":[{"module":"CIRCULATION"},{"config_name":"validation_rules"},{"code":"ABC"}]}

Notice that the query parameter 'query' is a standard MongoDB query as the configuration module is MongoDB based.


Add an entry:
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

Deleting / Updating specific entries is possible as well - See circulation.raml file. 
```

## Additional information

The [raml-module-builder](https://github.com/folio-org/raml-module-builder) framework.

Other [modules](http://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)
