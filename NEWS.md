## 5.7.2 2021-11-4

* [MODCONF-96](https://issues.folio.org/browse/MODCONF-96) Add other settings naming migration script

## 5.7.1 2021-09-28

 * [MODCONF-82](https://issues.folio.org/browse/MODCONF-92) Update RMB to 33.1.1 and Vert.x to 4.1.4

## 5.7.0 2021-05-27

No functional changes since 5.6.0.

 * Upgrade to Vert.x 4.1.0.CR1
 * [MODCONF-82](https://issues.folio.org/browse/MODCONF-82) Upgrade to RMB 33
 * [MODCONF-69](https://issues.folio.org/browse/MODCONF-69) Add personal data disclosure form

## 5.6.0 2021-01-01

 * [MODCONF-68](https://issues.folio.org/browse/MODCONF-68) Update to RMB 32 / Vert.x 4.0.0)
 * [MODCONF-56](https://issues.folio.org/browse/MODCONF-56) Remove unused default configuration settings (settings/locale)

## 5.5.0 2020-11-02

 * [MODCONF-65](https://issues.folio.org/browse/MODCONF-65) Upgrade to RMB 31.1.5

## 5.4.1 2020-07-14

 * [MODCONF-54](https://issues.folio.org/browse/MODCONF-54) RMB v30.2.4, fix "public.gin_trgm_ops" does not exist

## 5.4.0 2020-06-09

 * [MODCONF-53](https://issues.folio.org/browse/MODCONF-53) Update to RMB 30.0.2 and fix all code smells
 * [MODCONF-51](https://issues.folio.org/browse/MODCONF-51) Fix Maven ${} variable replacement in src/main/resources
 * [MODCONF-32](https://issues.folio.org/browse/MODCONF-32) Populate sample data for Tenant addresses

## 5.3.0 2019-12-18

 * [MODCONF-44](https://issues.folio.org/browse/MODCONF-44) POST item with id still makes server side ID. Version 5.2.0
   sample data must be wiped out, because the supplied ID in sample data
   was ignored.
 * [MODCONF-36](https://issues.folio.org/browse/MODCONF-36) Data migration script for changes to PO Number
   prefix/suffix entries

## 5.2.0 2019-12-04

 * [MODCONF-42](https://issues.folio.org/browse/MODCONF-42) Upgrade to RMB 29
 * [MODCONF-41](https://issues.folio.org/browse/MODCONF-41) Use JVM features to manage container memory
 * [MODCONF-35](https://issues.folio.org/browse/MODCONF-35) Fix PUT return 500 on entity not found
 * [MODCONF-33](https://issues.folio.org/browse/MODCONF-33) Use the tenant API for loading sample/reference data

## 5.1.0 2019-05-09

 * [MODCONF-29](https://issues.folio.org/browse/MODCONF-29) Upgrade to RMB 24

## 5.0.1 2018-10-02

 * [MODCONF-26](https://issues.folio.org/browse/MODCONF-26) Update to RAML 1.0 / RMB 21

## 5.0.0 2018-09-04

 * [MODCONF-21](https://issues.folio.org/browse/MODCONF-21) Configuration records are unique for each module, config name and code
 * [MODCONF-21](https://issues.folio.org/browse/MODCONF-21) Configuration records are enabled by default
 * [MODCONF-22](https://issues.folio.org/browse/MODCONF-22) Use dereferenced kv_configuration.schema for metadata sorting
 * Upgrades to RAML Module Builder 19.3.1
 * CQL queries including an unrecognised index report a 400 instead of 422

## 4.0.3 2018-04-24

 * [MODCONF-17](https://issues.folio.org/browse/MODCONF-17) 500 and 422 errors while querying configurations/audit

## 4.0.2 2018-03-03

w* [MODCONF-16](https://issues.folio.org/browse/MODCONF-16) Move to RMB 19 - generated sources will be moved to /classes

## 4.0.1 2018-02-19

 * [MODCONF-15](https://issues.folio.org/browse/MODCONF-15) Move to RMB 18
 * [MODCONF-14](https://issues.folio.org/browse/MODCONF-14) Unit test should stop embedded postgres when complete

## 4.0.0

 * [MODCONF-12](https://issues.folio.org/browse/MODCONF-12) Upgrade to RMB 16.0.2, read-only fields are removed
   and do not return a 422

## 3.0.0

 * [MODCONF-8](https://issues.folio.org/browse/MODCONF-8)
 * [MODCONF-7](https://issues.folio.org/browse/MODCONF-7)

## 2.0.0

 * [MODCONF-3](https://issues.folio.org/browse/MODCONF-3) mod-configuration accepts invalid fields in queries

## 1.0.1

 * [MODCONF-2](https://issues.folio.org/browse/MODCONF-2) Upgrade interface version from 0.0.9 to 1.0

## 1.0.0

 * [FOLIO-649](https://issues.folio.org/browse/FOLIO-649) User-specific preferences in mod-config
 * Align with camelCase naming conventions
