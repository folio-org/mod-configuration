## 5.12.0 2025-03-15

Sunflower release with dependency upgrades only:

 * [MODCONF-146](https://folio-org.atlassian.net/browse/MODCONF-146) Add folio-module-descriptor-validator to pom.xml
 * [FOLREL-615](https://folio-org.atlassian.net/browse/FOLREL-615) Update to mod-configuration Java 21
 * [MODCONF-149](https://folio-org.atlassian.net/browse/MODCONF-149) Upgrade all dependencies for Sunflower (R1-2025)

## 5.11.0 2024-10-23

Ramsons release with dependency upgrades only:

 * [MODCONF-148](https://folio-org.atlassian.net/browse/MODCONF-148): Ramsons deps: RMB 35.3.0, Vert.x 4.5.10, …

## 5.10.0 2024-03-12

 * [MODCONF-144](https://folio-org.atlassian.net/browse/MODCONF-144) Quesnelia deps: RMB 35.2.0, Vert.x 4.5.4, log4j 2.23.0, …

## 5.9.2 2023-10-06

Poppy release with dependency upgrades only:

 * [MODCONF-142](https://issues.folio.org/browse/MODCONF-142) Poppy dependencies: RMB 35.1.0, Vert.x 4.4.5, ...
 * [MODCONF-139](https://issues.folio.org/browse/MODCONF-139) Java 17

## 5.9.1 2023-02-09

 * [MODCONF-134](https://issues.folio.org/browse/MODCONF-134) RMB 35.0.5, Vert.x 4.3.7

## 5.9.0 2022-10-27

 * [MODCONF-127](https://issues.folio.org/browse/MODCONF-127) Upgrade to RMB 35.0.0 Vert.x 4.3.4

## 5.8.0 2022-06-20

 * [MODCONF-111](https://issues.folio.org/browse/MODCONF-111) RMB 34.0.0

## 5.7.9 2022-06-20

 * [MODCONF-113](https://issues.folio.org/browse/MODCONF-113) Revert RMB 34, allow PostgreSQL 10 for Lotus and Kiwi

## 5.7.8 2022-06-08

 * [MODCONF-109](https://issues.folio.org/browse/MODCONF-109) Publish javadoc and sources to maven repository
 * [MODCONF-110](https://issues.folio.org/browse/MODCONF-110) Don't generate ModuleName.java for mod-configuration-client
 * [MODCONF-111](https://issues.folio.org/browse/MODCONF-111) RMB 34.0.0, Vert.x 4.3.1
 * [MODCONF-112](https://issues.folio.org/browse/MODCONF-112) Fix ZipException on 64-bit systems (Lotus HF#1)

## 5.7.7 2022-04-26

 * [MODCONF-107](https://issues.folio.org/browse/MODCONF-107) RMB 33.2.9, Vert.x 4.2.7

## 5.7.6 2022-03-28

 * [MODCONF-106](https://issues.folio.org/browse/MODCONF-106) RMB 33.2.8, Vertx 4.2.6, jackson-databind 2.13.2.1 (CVE-2020-36518)

## 5.7.5 2022-02-09

 * [MODCONF-104](https://issues.folio.org/browse/MODCONF-104) RMB 33.2.5, Vert.x 4.2.4

## 5.7.4 2022-01-14

 * [MODCONF-103](https://issues.folio.org/browse/MODCONF-103) RMB 33.2.4, Vert.x 4.2.3, log4j 2.17.1 fixing [] log entries

## 5.7.3 2021-12-15

 * [MODCONF-102](https://issues.folio.org/browse/MODCONF-102) Upgrade to RMB 33.2.2 Log4j 2.16.0
 * [MODCONF-98](https://issues.folio.org/browse/MODCONF-98) RMB 33.2.1, Vert.x 4.2.1, Log4j 2.15.0 (CVE-2021-44228)

## 5.7.2 2021-11-08

 * [MODCONF-96](https://issues.folio.org/browse/MODCONF-96) Add other settings naming migration script

## 5.7.1 2021-09-28

 * [MODCONF-92](https://issues.folio.org/browse/MODCONF-92) Update RMB to 33.1.1 and Vert.x to 4.1.4

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
