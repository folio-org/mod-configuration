# mod-configuration


Copyright (C) 2016-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file ["LICENSE"](https://github.com/folio-org/mod-configuration/blob/master/LICENSE) for more information.

## Purpose

Configuration module based on the raml-module-builder and a set of raml and json schemas backed by a PostgreSQL async implementation

## Deprecation

<div style="color: red; background: #ffd0d0; padding: 0.4em 1em; font-weight: bold">
This module is deprecated. Please do not add new configuration values to this module.
</div>

Instead, consider either:
* Using [mod-settings](https://github.com/folio-org/mod-settings), a conceptually similar centralized-configuration module which fixes [the security flaw](https://github.com/MikeTaylor/folio-docs/blob/main/doc/fixing-mod-configuration.md#backward-compatibility-and-migration) that makes this module unsuitable.
* Creating CRUD APIs to store configuration and settings values in the storage module they belong to.

At present (May 2023) [a Technical Committee working group](https://wiki.folio.org/display/TC/TC+Subgroup%3A+Distributed+vs.+Centralized+Configuration) is determining which of these two courses to recommend under which circumstances. Until that group returns its recommendations, developers should use their own judgement, but should definitely avoid mod-configuration.

## Permission warning

The permission granularity is too coarse.  Permission is always granted to all values, there is no way to grant permission to only a selected set of values.  This applies to read access, and it also applies to write access.

Therefore don't store passwords or other confidential or critical values in mod-configuration.  See previous section for other options.

## Introduction

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

Module| configName | updatedBy | default | enabled | code | value | desc | userId
------------ | ------------- | -------------  | -------------  | -------------  | -------------  | -------------  | ------------- | -------------
 |  |
CIRCULATION| import.uploads.files | Joe | false | true | path_2_file | PENDING | file to import | uid
CIRCULATION| patron.drools | Joe | false | true | rule_name1 | base64enc_drools_file| rule file |
CIRCULATION| patron.drools | Joe | false | true | rule_name2 | base64enc_drools_file| rule file | uid

The above table can be interpreted as follows:

Module: **CIRCULATION**

&nbsp;&nbsp;&nbsp;Config name: **patron.drools**

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; row: **rule_name1** (the code of the row)

Note that each tenant has its own schema with its own configuration tables. The userId field can be populated to associate an entry in the table with a specific user.

see configuration schema for an object description:
https://github.com/folio-org/mod-configuration/blob/master/ramls/_schemas/kv_configuration.schema

### Instructions

clone / download mod-configuration then `mvn clean install`

Run:

`java -jar mod-configuration-server/target/mod-configuration-server-fat.jar -Dhttp.port=8081


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
      <version>1.0.0</version>
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
The configuration module supports the [CQL (Contextual Query Language)](https://github.com/folio-org/raml-module-builder#cql-contextual-query-language) syntax.

### Auditing
Every change to entries is automatically audited by the service.
To see an audit list:

`http://<host>:<port>/configurations/audit`

#### Querying audit records

CQL syntax is also supported by the audit API

### <a id="local-apidocs"></a>Documentation of the Service's APIs

Documentation is auto-generated from the RAML files into HTML.
After the service is started, the documentation can be viewed at:

http://localhost:8081/apidocs/index.html?raml=raml/configuration/config.raml

### Examples

Make sure to include appropriate headers as the runtime framework validates them.

`Accept: application/json`

`Content-Type: application/json`


```sh

Query for all tables:
(GET)
http://localhost:8081/configurations/entries


Query for a specific module / config / row:
(GET)
http://localhost:<port>/configurations/entries?query=code=PATRON_RULE



Add an entry:
(POST)
http://localhost:8081/configurations/entries
{
  "module": "CIRCULATION",
  "configName": "validation_rules",
  "updatedBy": "joe",
  "code": "PATRON_RULE",
  "description": "for patrons",
  "default": true,
  "enabled": true,
  "value": "any value"
}

Deleting / Updating specific entries is possible as well - See circulation.raml file.
```

## Additional information

### Types of Configuration Records
#### Tenant
These are records which are not associated with a user (no `userId` property).

They represent a configuration setting for a tenant, and is the default if no user setting is in place (see validation section for what how records are intended to be matched).

#### User
These are records which are associated with a user (a `userId` property is present).

They represent a configuration setting for a specific user, which is considered to take precedence over a matching tenant setting (if present).

### Defaults

#### Enabled
Configuration records are defaulted to be enabled (`enabled` is true) if the client does not provide a value for the `enabled` property.

This applies to both newly created records, and records being replaced using PUT.

### Validation
As of version 5.0.0, configuration records are validated to be unique for combinations of certain properties.

Disabled properties (`enabled` is false) are ignored during these checks.

These checks are applied separately for tenant and user level records, in order for it to be possible to have user level record precedence for the same setting.

#### Module and Config Name
If no code is present, a setting is considered to be unique for the `module` and `configName` properties.

#### Module, Config Name and Code
If a code is present, a setting is considered to be unique for the `module`,  `configName` and `code` properties.

#### Implementation

These checks are achieved by using four unique indexes.

Two of these are for the two variations above at the tenant level configurations and the other two at the user level.

See the [declarative schema](mod-configuration-server/src/main/resources/templates/db_scripts/schema.json) for how these are defined.

### Other documentation

The [raml-module-builder](https://github.com/folio-org/raml-module-builder) framework.

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-configuration)
and the [local docs](#local-apidocs).

### Issue tracker

See project [MODCONF](https://issues.folio.org/browse/MODCONF)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### Quick start

Compile with `mvn clean install` and see further [instructions](#instructions).

Run the local stand-alone instance:

```
java -jar mod-configuration-server/target/mod-configuration-server-fat.jar \
  -Dhttp.port=8081 embed_postgres=true
```

Additional command-line [options](#instructions) and information.

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-configuration).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts/) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-configuration/).

