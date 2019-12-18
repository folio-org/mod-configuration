## 5.3.0 2019-12-18

 * MODCONF-44 POST item with id still makes server side ID. Version 5.2.0
   sample data must be wiped out, because the supplied ID in sample data
   was ignored.
 * MODCONF-36 Data migration script for changes to PO Number
   prefix/suffix entries

## 5.2.0 2019-12-04

 * MODCONF-42 Upgrade to RMB 29
 * MODCONF-41 Use JVM features to manage container memory
 * MODCONF-35 Fix PUT return 500 on entity not found
 * MODCONF-33 Use the tenant API for loading sample/reference data

## 5.1.0 2019-05-09

 * MODCONF-29 Upgrade to RMB 24

## 5.0.1 2018-10-02

 * MODCONF-26 Update to RAML 1.0 / RMB 21

## 5.0.0 2018-09-04

 * MODCONF-21 Configuration records are unique for each module, config name and code
 * MODCONF-21 Configuration records are enabled by default
 * MODCONF-22 Use dereferenced kv_configuration.schema for metadata sorting
 * Upgrades to RAML Module Builder 19.3.1
 * CQL queries including an unrecognised index report a 400 instead of 422

## 4.0.3 2018-04-24

 * MODCONF-17 500 and 422 errors while querying configurations/audit

## 4.0.2 2018-03-03

w* MODCONF-16 Move to RMB 19 - generated sources will be moved to /classes

## 4.0.1 2018-02-19

 * MODCONF-15 Move to RMB 18
 * MODCONF-14 Unit test should stop embedded postgres when complete

## 4.0.0

 * MODCONF-12 Upgrade to RMB 16.0.2, read-only fields are removed
   and do not return a 422

## 3.0.0

 * MODCONF-8
 * MODCONF-7

## 2.0.0

 * MODCONF-3 mod-configuration accepts invalid fields in queries

## 1.0.1

 * MODCONF-2 Upgrade interface version from 0.0.9 to 1.0

## 1.0.0

 * FOLIO-649 User-specific preferences in mod-config
 * Align with camelCase naming conventions
