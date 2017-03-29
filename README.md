# mod-configuration


Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file ["LICENSE"](https://github.com/folio-org/mod-configuration/blob/master/LICENSE) for more information.


#### Configuration module based on the raml-module-builder and a set of raml and json schemas backed by a PostgreSQL async implementation

This project is built using the raml-module-builder, using the PostgreSQL async client to implement some basic configuration APIs. It is highly recommended to read the [raml-module-builder README](https://github.com/folio-org/raml-module-builder/blob/master/README.md) since there are many features that the mod-configuration module inherits from the raml-module-builder framework.

The idea behind this module is to provide a type of centralized configuration service. The service allows for the creation of module configurations. Within a module there are named configurations, and within a named configuration there are 1..N 'rows'.

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

The above table can be interpreted as follows:

Module: **CIRCULATION**

&nbsp;&nbsp;&nbsp;Config name: **patron.drools**

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; row: **rule_name1** (the code of the row)

see configuration schema for an object description:
https://github.com/folio-org/mod-configuration/blob/master/ramls/_schemas/kv_configuration.schema

### Instructions

clone / download mod-configuration then `mvn clean install`

Run:

`java -jar mod-configuration-server/target/mod-configuration-server-fat.jar -Dhttp.port=8085 embed_postgres=true`


Or run via Dockerfile

The Configuration service can be run in either embedded PostgreSQL mode or with a regular PostgreSQL server.

Note that the embedded PostgreSQL is started on a static port (6000)

### Connecting to an existing configuration service

The configuration module also comes with a statically typed Java client.
To use the client via maven, add:

```sh
    <dependency>
      <groupId>org.folio</groupId>
      <artifactId>mod-configuration-client</artifactId>
      <version>0.0.5-SNAPSHOT</version>
    </dependency>
```

```sh
ConfigurationsClient cc = new ConfigurationsClient("config.server.host", port, "mytenantid");

cc.getEntries("module==CIRCULATION", 0, 10, "en", response -> {
  response.bodyHandler(body -> {
    System.out.println(body);
  });
});

String content = getFile("kv_configuration.sample");
Config conf = new ObjectMapper().readValue(content, Config.class);
cc.postEntries(null, conf, reply -> {
  reply.bodyHandler( handler -> {
   System.out.println(new String(handler.getBytes(), "UTF8"));
  });
});
```

#### Query syntax
The configuration module supports the CQL syntax - please see
https://github.com/folio-org/cql2pgjson-java

### Auditing
Every change to entries is automatically audited by the service.
To see an audit list:

`http://<host>:<port>/configurations/audit`

CQL syntax is also supported by the audit API

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
http://localhost:8085/configurations/entries


Query for a specific module / config / row:
(GET)
http://localhost:<port>/configurations/entries?query=scope.institution_id=aaa



Add an entry:
(POST)
http://localhost:8085/configurations/entries
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
