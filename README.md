# mod-configuration
Demo configuration module based on the raml-module-builder and a set of raml and json schemas backed by a mongoDB async implementation

This project is built using the raml-module-builder, using the MongoDB async client to implement some basic configuration APIs. It is highly recommended to read the [raml-module-builder README](https://github.com/folio-org/raml-module-builder/blob/master/README.md)

The idea behind this module is to provide a sample of a configuration service. The service allows to create module configurations. Within a module there are named configurations, and within a configuration there are 1..N rows. 

Module -> 1...n configs -> 1...n rows

Can be run in both embedded mongodb mode or with a regular MongoDB server

instructions:

clone / download the raml-module-builder and `mvn clean install`

then do the same for the current project `mvn clean install`

Run:

`java -jar configuration-fat.jar -Dhttp.port=8085 embed_mongo=true`


Or via dockerfile

Note that the embedded mongo is started on a dynamic port chosen at embedded mongo start up - refer to the log ("created embedded mongo config on port 54851")


documentation of the APIs can be found at:

http://localhost:8085/apidocs/index.html?raml=raml/configuration/config.raml

Examples:

Make sure to include appropriate headers as the runtime framework validates them

Authorization: aaaaa

Accept: application/json

Content-Type: application/json

```sh

get all tables

http://localhost:8085/apis/configurations/tables


add a module / config pair for circulation / validation rules with 2 rows

http://localhost:8085/apis/configurations/tables		
{
  "module": "CIRCULATION",
  "name": "validation_rules",
  "description": "validate content",
  "updated_by": "joe",
  "update_date": "2016.06.27.10.56.03",
  "scope": {
	"institution_id" : "aaa",
	"library_id" : "vvv"
  },
  "rows": [
	{
	  "code": "PATRON_RULE2",
	  "description": "for patrons2",
	  "default": true,
	  "enabled": true,
	  "value": "123"
	},
	{
	  "code": "PATRON_RULE2",
	  "description": "for patrons2",
	  "default": true,
	  "enabled": true,
	  "value": "12345"
	}
  ]
}

add rows to the existing module (circ) / config (validation rules)

http://localhost:8085/apis/configurations/tables/module/CIRCULATION/name/validation_rules

{
  "module": "CIRCULATION",
  "name": "validation_rules",
  "scope": {
	"institution_id" : "aaa",
	"library_id" : "vvv"
  },
  "rows": [
	{
	  "code": "PATRON_RULE3",
	  "description": "for patrons2",
	  "default": true,
	  "enabled": true,
	  "value": "123"
	},
					{
	  "code": "PATRON_RULE4",
	  "description": "for patrons2",
	  "default": true,
	  "enabled": true,
	  "value": "123"
	}
  ]
}

query for a specific module / config / row 

http://localhost:8085/apis/configurations/tables?query={"$and": [ { "module": "CIRCULATION"}, { "name": "validation_rules"}, { "rows.code": { "$all": [ "PATRON_RULE" ] } }]}


```


