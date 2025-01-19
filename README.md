# Jasper
Knowledge Management Server

[![Build & Test](https://github.com/cjmalloy/jasper/actions/workflows/test.yml/badge.svg)](https://cjmalloy.github.io/jasper/reports/latest-junit/)
[![Gatling](https://github.com/cjmalloy/jasper/actions/workflows/gatling.yml/badge.svg)](https://cjmalloy.github.io/jasper/reports/latest-gatling/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-1.3.0-brightgreen)](https://editor.swagger.io/?url=https://raw.githubusercontent.com/cjmalloy/jasper/refs/heads/master/src/main/resources/swagger/api.yml)
[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/jasper)](https://artifacthub.io/packages/helm/jasper/jasper)

## Quickstart
To start the server, client and database with a single admin user, run
the [quickstart](https://github.com/cjmalloy/jasper-ui/blob/master/quickstart/docker-compose.yaml)
docker compose file. See [Jasper App](https://github.com/cjmalloy/jasper-app) for an installable
electron wrapper.

## Knowledge Management
Jasper is an open source knowledge management (KM) system. A KM system is similar to a Content Management
System (CMS), but it does not store any content. Instead, a KM stores links to content. This means
that adding a KM to your internal tools is quick and easy. It will create an overlay database, 
which is a small and fast index of all your content sources. Extend functionality with custom plugins,
or embed existing dashboard panels directly to create your central business intelligence dashboard.

See [Jasper-UI](https://github.com/cjmalloy/jasper-ui) for documentation on the reference client.

### Centralized Business Intelligence
Dumping all department-level data into a central data lake to perform analytics on is a massive undertaking
with dubious potential benefit. Instead, empower departments to run their own analytics and formalize the
reporting format to allow centralized aggregation.

Build a Business Intelligence (BI) dashboard without building a data lake. Business departments can use
both a push or pull model to publish their analytics, reports, results, KPIs, graphs, metrics or alerts.
Jasper standardises the transport, storage, searching, indexing, and retrieval of data while allowing you
to use your existing data structures and formats. Stitch together department-level resources to create
a central overview that explicitly describes dependencies.

### Security
Jasper uses Tag Based Access Control (TBAC) to assign fine grained access controls to any object in the
system. This system is simple and powerful, such that the entire security specification is contained
in a [small, readable file](https://github.com/cjmalloy/jasper/blob/master/src/main/java/jasper/security/Auth.java).

### Build your own client
Connect to Jasper with a custom client to give users a streamlined user experience (UX). Frontend
developers can create a bespoke interface without needing to make any server side changes. Create custom
plugins and templates and ensure data shape with [JTD](https://jsontypedef.com/docs/jtd-in-5-minutes/)
schemas. Fork [the reference client](https://github.com/cjmalloy/jasper-ui) or use the
[OpenApi docs](https://editor.swagger.io/?url=https://raw.githubusercontent.com/cjmalloy/jasper/refs/heads/master/src/main/resources/swagger/api.yml) to generate API stubs.

## Standards
Jasper is a standard data model and API. While JSON is used in this document, Jasper may be generalised
to other presentations, such as XML, YAML, or TOML.
Jasper defines five entity types, an access control model, and a plugin/templating system for extending
the model.
1. Ref
2. Ext
3. User
4. Plugin
5. Template

The main entity is the Ref, it represents a reference to an external resource. The main field in a Ref
is the URL field which can be a link to a web page, or a reference to any arbitrary resources predicated
by the URL scheme. Web content will of course use the http or https scheme. To reference a book,
one could use the [ISBN](https://en.wikipedia.org/wiki/ISBN) scheme (i.e. `isbn:978-3-16-148410-0`).
For comments, [Jasper-UI](https://github.com/cjmalloy/jasper-ui) uses a `comment` scheme followed by an arbitrary ID, usually a UUID
(i.e. `comment:75b36465-4236-4d64-8c78-027d87f3c072`). For hosting internal wikis, 
[Jasper-UI](https://github.com/cjmalloy/jasper-ui) uses a `wiki` scheme followed by the
[Wiki Page Name](https://en.wikipedia.org/wiki/Wikipedia:Page_name) (i.e. `wiki:John_Cena`).

Like the [OSI model](https://en.wikipedia.org/wiki/OSI_model), Jasper's data model is defined in layers:
1. **Identity Layer** - Structure and Persistence of entities
2. **Indexing Layer** - Defining optional fields used to query, sort, filter, and transport
3. **Validation Layer** - plugins and templates are validated
4. **Modding Layer** - custom plugins, templates, and clients

## Tagging
Jasper support hierarchical tagging of Refs. Tags are not entities, they are strings with
regex `[_+]?[a-z0-9]+([./][a-z0-9]+)*`. Tags are part of the primary key for Tag-like entities, but no
entities need exist to use a tag.  
Refs have a list of tags which can be used for categorization, permissions, and plugins.  
There are three types of tags, which the type defined as a semantic ontology:
`public`, `+protected`, `_private` tags. The character prefix defines the type while also being
part of the tag itself. Therefore, no lookup is ever required to determine the tag type.
 * A public tag can be used freely by anyone. This includes tagging a Ref, or using it in a query.
 * A protected tag can freely be used in a query, but you cannot tag a Ref with a protected tag
unless it is in your [read access](#access-control) list.
 * A private tag cannot be used at all unless permission is given. When fetching a Ref that includes
private tags, they will be removed by the server prior to sending. See
[access control](#access-control) for more.

Tags may also be fully qualified by appending the origin. (i.e. `tag@origin`).  
Use forward slashes to define hierarchical tags (i.e. `people/murray/bill` or  `people/murray/anne`)

## Querying
When fetching a page or Refs a query may be specified. The query language uses simple set-like
operators to match Refs according to their tag list and Origin. You may use tags, origins, or
fully qualified tags (tag + origin). There is a special origin `@` which will match the
default origin `""` (the empty string).  
If a tag is not fully qualified it will match the wildcard origin `"@*"`. The `*`
wild card can be used to match anything on the default origin `""` (empty string).
Valid operators in a query are:
1. `:` and
2. `|` or
3. `!` not
4. `()` groups

Note: In the current implementation, groups may not be nested.

Example queries:
 * `science`: All Refs that include the `science` tag
 * `science|funny`: All Refs that have either the `science` tag or the `funny` tag
 * `science:funny`: All Refs that have both the `science` tag and the `funny` tag
 * `science:!funny`: All Refs that have the `science` tag but do not have the `funny` tag
 * `(science|math):funny`: All Refs that have either the `science` or `math` tags, but
also the `funny` tag. This would match a ref with `['science', 'funny']`, `['math', 'funny']`,
but would not match `['science', 'math']`
 * `science:funny|math:funny`: Expended form of previous query. Would produce the exact same results.
 * `music:people/murray`: All Refs that have the `music` tag and `people/murray` tag. It would also
match Refs with `['music', 'people/murray/anne']` or `['music', 'people/murray/bill']`

## Modding
Jasper allows extensive modification with server reuse. Since changes are done by creating
Plugin and Template entities, server restarts are not required.  
This method of modding means that only client changes are required. The same Jasper server,
without any code modifications, can be used. The client can define and support its own Plugins
and Templates. This allows for much more flexible development, as writing client code (in particular
web clients) is much easier than writing server code. A developer with only front-end expertise 
can extend the Jasper model to support arbitrary applications.  
In order to extend the functionality of a Ref, a developer may choose a set of tags or URL scheme
and a convention by which they modify the semantics of a Ref. If a custom data model is also
required, a Plugin entity may be created which defines a
[JTD](https://jsontypedef.com/docs/jtd-in-5-minutes/) schema. A Plugin is a Tag-like entity. When
a Ref is tagged with a Plugin, the Plugin may be considered active for that Ref. The Ref may then
store data in its config field and the server will validate it according to the schema.  
Similarly, Ext entities may be created which extend the functionality of a tag. As Plugins define
custom data that can be stored in a ref, Templates may be created which allow custom data to be
stored in Ext entities and similarly validated according to their schema.

See [Jasper-UI](https://github.com/cjmalloy/jasper-ui) for examples of Plugins and Templates, such as:
* `plugin/thumbanail`: [This plugin](https://github.com/cjmalloy/jasper-ui/blob/master/src/app/mods/thumbnail.ts)
allows a Ref to include a URL to a thumbnail image.
* `user` Template: 
[This template](https://github.com/cjmalloy/jasper-ui/blob/master/src/app/mods/user.ts)
allows a user tag to customize their experience, such as subscribing to a list of tags to show
on their home page.

## Entities
There are two types of entities in Jasper:
1. Refs
2. Tags (including Exts, Plugins, Templates, and Users)

![entities](./docs/entities.png)
Origins are used to facilitate replication and multi-tenant operation. Each origin represents a
jasper instance that that entity originated from.
![origins](./docs/origins.png)

### Ref
Refs are the main data model in Jasper. A Ref defines a URL to a remote resource. Example:
```json 
{
  "url": "https://www.youtube.com/watch?v=9Gn4rmQTZek",
  "origin": "",
  "title": "Why does Science News Suck So Much?",
  "comment": "Sabine Hossenfelder",
  "tags": ["public", "youtube", "sabine"],
  "sources": [],
  "alternateUrls": [],
  "plugins": {
    "plugin/thumbnail": {"url": "https://...jpg"}
  },
  "metadata": {
    "responses": 0,
    "internalResponses": 0,
    "plugins": {},
    "modified": "2022-06-18T12:07:04.404272Z"
  },
  "published": "2022-06-18T12:00:07Z",
  "created": "2022-06-18T12:07:04.404272Z",
  "modified": "2022-06-18T12:07:04.404272Z"
}
```
Only the "url", "origin", "created", "modified", and "published" fields are required.

The combination of URL (including Alternate URLs) and Origin for this Ref must be unique and may
be used as a Primary Composite Key. Implementations may also make the modified date part of the
composite primary key for version history.

**URL:** The url of the resource.  
**Origin:** The Origin this Ref was replicated from, or the empty string for local.  
**Title:** Optional title for this Ref.  
**Comment:** Optional comment for this Ref, usually markdown.  
**Tags:** A list of tags used to categorise this Ref. All tags must match the regex `[_+]?[a-z0-9]+([./][a-z0-9]+)*`  
**Sources:** A list of URLs which are sources for this Ref. These may or may not have a corresponding Ref
entity. If a source URL does correspond to a Ref, the published date of the source must predate the
published date of this Ref.  
**Alternate URLs:** Alternate URLs which should be considered synonymous with the URL of this Ref. This
should be used as part of a uniqueness check when ingesting Refs.  
**Plugins:** A JSON object with plugin tags as fields and arbitrary JSON data defined by each respective
plugin. Must be valid according to each plugin's schema.  
**Metadata:** Optional data generated by the server for this resource. Includes response links (inverse
source lookup).  
**Published:** The published date of this resource. Default to create date if not known. This date must
be later than the published date of all sources.  
**Created:** Created date of this Ref.  
**Modified:** Last modified date of this Ref. If this is the same as the created date no modification
has occurred. Does not update if Metadata is modified.  

### Ext
An Ext is a Tag-like entity representing a Tag extension.
```json 
{
  "tag": "news",
  "origin": "",
  "name": "News",
  "config": {
    "pinned":[],
    "sidebar": ""
  },
  "modified": "2022-06-18T16:00:59.978700Z"
}
```
Only the "tag", "origin", and "modified" fields are required.

An Ext allows you to customise a Tag page. For example, you could set the sidebar text or pin some links.

**Tag:** The tag of this Ext. Must match the regex `[_+]?[a-z0-9]+([./][a-z0-9]+)*`
**Origin:** The Origin this Ext was replicated from, or the empty string for local.
**Name:** The display name of this Ext. Used to customise the page title for the Tag page.
**Config:** Arbitrary JSON data defined by Templates. Must be valid according to each template's schema.
**Modified:** Last modified date of this Ext

### User
A User is a Tag-like entity representing a user.
```json 
{
  "tag": "+user/charlie",
  "origin": "",
  "name": "Charlie Brown",
  "readAccess": [],
  "writeAccess": [],
  "tagReadAccess": [],
  "tagWriteAccess": [],
  "pubKey": "...",
  "modified": "2022-06-18T16:00:59.978700Z"
}
```
Only the "tag", "origin", and "modified" fields are required.

A User contains the access control information for the system. Access tags work in all
sub-origins.

**Tag:** The tag of this User. Must match the regex `[_+]user/[a-z0-9]+([./][a-z0-9]+)*`  
**Origin:** The Origin this User was replicated from, or the empty string for local.  
**Name:** The display name of this User. Used to customise the page title for the Tag page.  
**Read Access:** List of tags this user has complete read access to. Grants read access to all
entities with this tag.  
**Write Access:** List of tags this user has complete write access to. Grants write access to
all entities with this tag.  
**Tag Read Access:** List of tags this user can read. Only applies to Tag-like entities. Only needed
for private tags.  
**Tag Write Access:** List of tags this user can write. Only applies to Tag-like entities.  
**Pub Key:** Base 64 encoded public RSA key. Used for verifying signatures to validate authorship.  
**Modified:** Last modified date of this User.  

### Plugin
A Plugin is a Tag-like entity used to extend the functionality of Refs.
```json 
{
  "tag": "plugin/thumbnail",
  "origin": "",
  "name": "⭕️ Thumbnail",
  "config": {...},
  "defaults": {},
  "schema": {
    "optionalProperties": {
      "url": {"type": "string"},
      "width": {"type": "int32", "nullable": true},
      "height": {"type": "int32", "nullable": true}
    }
  },
  "generateMetadata": false,
  "userUrl": false,
  "modified": "2022-06-18T16:27:13.774959Z"
}
```
Only the "tag", "origin", and "modified" fields are required.

Tagging a ref with a Plugin tag applies that plugin to the Ref. The Ref plugin must contain valid
data according to the Plugin schema.  

**Tag:** The tag of this Plugin. Must match the regex `[_+]?plugin/[a-z0-9]+([./][a-z0-9]+)*`  
**Origin:** The Origin this Plugin was replicated from, or the empty string for local.  
**Name:** The display name of this Ext. Used to customise the page title for the Tag page.  
**Config:** Arbitrary JSON.  
**Defaults:** Default plugin data if creating a new Ref with empty plugin data.  
**Schema:** Json Type Def (JTD) schema used to validate plugin data in Ref.  
**Generate Metadata:** Flag to indicate Refs should generate a separate inverse source lookup for
this plugin in all Ref metadata.  
**User Url:** Flag to only allow this plugin on a User Url, which is a specially constructed URL
of the form `tag:/{tag}?user={user}`. This has the effect of restricting the plugin to one Ref per user.
**Modified:** Last modified date of this Plugin.  

### Template
A Template is a Tag-like entity used to extend the functionality of Exts.
```json 
{
  "tag": "",
  "origin": "",
  "name": "Default Template",
  "config": {...},
  "defaults": {
    "pinned": []
  },
  "schema": {
    "properties": {
    "pinned": {"elements": {"type": "string"}}
  },
  "optionalProperties": {
    "sidebar": {"type": "string"}
    }
  },
  "modified": "2022-06-18T16:27:13.774959Z"
}
```
Only the "tag", "origin", and "modified" fields are required.

The Tag in the case of a template is actually a Tag prefix. This Template matches all Exts
where its tag followed by a forward slash is a prefix of the Ext tag. In the case of the empty
string the Template matches all Exts.

**Tag:** The tag of this Template. Must match the regex `[_+]?[a-z0-9]+([./][a-z0-9]+)*` or the empty string.  
**Origin:** The Origin this Template was replicated from, or the empty string for local.  
**Name:** The display name of this Template.  
**Config:** Arbitrary JSON.  
**Defaults:** Default Ext config if creating a new Ext with empty config.  
**Schema:** Json Type Def (JTD) schema used to validate Ext config.  
**Modified:** Last modified date of this Template.

## Layers
The jasper model is defined in layers. This is to facilitate lower level operations such as routing, querying
and archiving.

### Identity Layer
The identity layer of the Jasper model defines how entities are stored or retrieved. A system operating
at this layer should be extremely lenient when validating entities. Only the identity fields of the
entity need to be considered. The identity fields are:  
1. Refs: (URL, Origin, Modified)
2. Tags: (Tag, Origin, Modified)

Together, the (Origin, Modified) keys represent the cursor of the entity, which is used in origin based
replication. 

### Indexing Layer
The indexing layer of the Jasper model adds tags to Refs. A system operating at this layer should support
tag queries, sorting and filtering.

### Validation Layer
The validation layer of the Jasper model includes all entity fields. Plugins and Templates are validated
according to their schema.

#### Plugin and Template Inheritance
Plugins and Templates behave differently in how they inherit the fields of the parent Ext.
Plugins stack and templates merge.
For example, the Plugin `plugin/test` like:
```json
{
  "tag": "plugin/test",
  "schema": {
    "properties": {
      "test": { "type":  "string" }
    }
  }
}
```
And the Plugin `plugin/test/this` like:
```json
{
  "tag": "plugin/test/this",
  "schema": {
    "properties": {
      "more": { "type":  "string" }
    }
  }
}
```
If we use both of these plugins in the same Ref, both plugins would have their
data stacked, like:
```json
{
  "url": "test:1",
  "plugins": {
    "plugin/test": {
      "test": "data"
    },
    "plugin/test/this": {
      "more": "tests"
    }
  }
}
```
A template would merge all fields, overwriting at every stage, into a final result.
For example, the Template `a` like:
```json
{
  "tag": "a",
  "schema": {
    "properties": {
      "test": { "type":  "string" }
    }
  }
}
```
And the Template `a/b` like:
```json
{
  "tag": "a/b",
  "schema": {
    "properties": {
      "more": { "type":  "string" }
    }
  }
}
```
If we use both of these plugins in the same Ext, both plugins would have their
data merged, like:
```json
{
  "tag": "a/b/c",
  "config": {
    "test": "data",
    "more": "tests"
  }
}
```
If a child Template defines an overlapping field in the schema, it will override the parent type.

### Modding Layer
The modding layer of the Jasper model is entirely client side. No server changes are required in order to
support new plugins or templates.

## Cursor Replication
Distributed systems must make tradeoffs according to the [CAP theorem](https://en.wikipedia.org/wiki/CAP_theorem).
According to the CAP theorem you may only provide two of these three guarantees: consistency, availability,
and partition tolerance. Jasper uses an eventually consistent model, where availability and partition
tolerance are guaranteed. The modified date is used as a cursor to efficiently poll for modified records.

To replicate a Jasper instance simply create a Ref for that instance and tag it `+plugin/origin/pull`. If
either the `pull-burst` or `pull-schedule` profiles are active the jasper server will then poll that
instance periodically to check for any new entities. The modified date of the last entity received will
be stored and used for the next poll. When polling, the Jasper server requests a batch of entities from
the remote instance where the modified date is after the last stored modified date, sorted by modified
date ascending. Users with the `MOD` role may also initiate a scrape.

### Duplicate Modified Date
Jasper instances should enforce unique modified dates as the cursor for each entity type. Otherwise,
when receiving
a batch of entities, it's possible that the last entity you received has a modified date that is
exactly the same as another entity. If that is the case, requesting the next batch after that modified
date will skip such entities.

To prevent duplicate modified dates it's enough to add a single millisecond to the date until it
is unique.

## Deployment
Jasper is available in the following distributions:
 - [Docker image](https://github.com/cjmalloy/jasper/pkgs/container/jasper)
 - [Helm chart](https://artifacthub.io/packages/helm/jasper/jasper-ui)
 - [Jar](https://github.com/cjmalloy/jasper/releases/latest)

It supports the following configuration options:

| Environment Variable                           | Description                                                                                                                    | Default Value (in prod)                                                                                                                                                                                       |
|------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SERVER_PORT`                                  | Port to listen for HTTP connections.                                                                                           | `8081`                                                                                                                                                                                                        |
| `SPRING_PROFILES_ACTIVE`                       | Set the comma separated list of runtime profiles.                                                                              | `default`                                                                                                                                                                                                     |
| `SPRING_DATASOURCE_URL`                        | PostgreSQL database connection string.                                                                                         | `jdbc:postgresql://localhost:5432/jasper`                                                                                                                                                                     |
| `SPRING_DATASOURCE_USERNAME`                   | PostgreSQL database username.                                                                                                  | `jasper`                                                                                                                                                                                                      |
| `SPRING_DATASOURCE_PASSWORD`                   | PostgreSQL database password.                                                                                                  |                                                                                                                                                                                                               |
| `JASPER_DEBUG`                                 |                                                                                                                                | `false`                                                                                                                                                                                                       |
| `JASPER_INGEST_MAX_RETRY`                      | Maximum number of retry attempts for getting a unique modified date when ingesting a Ref.                                      | `5`                                                                                                                                                                                                           |
| `JASPER_MAX_ETAG_PAGE_SIZE`                    | Max number of results in a page before calculating an Etag is no longer attempted.                                             | `300`                                                                                                                                                                                                         |
| `JASPER_BACKUP_BUFFER_SIZE`                    | Size of buffer in bytes used to cache JSON in RAM before flushing to disk during backup.                                       | `1000000`                                                                                                                                                                                                     |
| `JASPER_RESTORE_BATCH_SIZE`                    | Number of entities to restore in each transaction.                                                                             | `500`                                                                                                                                                                                                         |
| `JASPER_BACKFILL_BATCH_SIZE`                   | Number of entities to generate Metadata for in each transaction when backfilling.                                              | `1000`                                                                                                                                                                                                        |
| `JASPER_CLEAR_CACHE_COOLDOWN_SEC`              | Number of seconds to throttle clearing the config cache.                                                                       | `2`                                                                                                                                                                                                           |
| `JASPER_PUSH_COOLDOWN_SEC`                     | Number of seconds to throttle pushing after modification.                                                                      | `1`                                                                                                                                                                                                           |
| `JASPER_LOCAL_ORIGIN`                          | The origin of this server, unless overridden in a header or auth token.                                                        | `false`                                                                                                                                                                                                       |
| `JASPER_ALLOW_LOCAL_ORIGIN_HEADER`             | Allow overriding the local origin via the `Local-Origin` header. Only set this if you set in reverse proxy.                    | `false`                                                                                                                                                                                                       |
| `JASPER_ALLOW_USER_TAG_HEADER`                 | Allow pre-authentication of a user via the `User-Tag` header.                                                                  | `false`                                                                                                                                                                                                       |
| `JASPER_ALLOW_USER_ROLE_HEADER`                | Allows escalating user role via `User-Role` header.                                                                            | `false`                                                                                                                                                                                                       |
| `JASPER_ALLOW_AUTH_HEADERS`                    | Allow adding additional user permissions via `Read-Access`, `Write-Access`, `Tag-Read-Access`, and `Tag-Write-Access` headers. | `false`                                                                                                                                                                                                       |
| `JASPER_MAX_ROLE`                              | Highest role allowed to access the server. Users with a higher role will have their role reduced to this.                      | `ROLE_ANONYMOUS`                                                                                                                                                                                              |
| `JASPER_MIN_ROLE`                              | Minimum role required to access the server.                                                                                    | `ROLE_ANONYMOUS`                                                                                                                                                                                              |
| `JASPER_MIN_WRITE_ROLE`                        | Minimum role required to write to the server.                                                                                  | `ROLE_ANONYMOUS`                                                                                                                                                                                              |
| `JASPER_DEFAULT_ROLE`                          | Default role given to all users.                                                                                               | `ROLE_ANONYMOUS`                                                                                                                                                                                              |
| `JASPER_DEFAULT_READ_ACCESS`                   | Additional read access qualified tags to apply to all users.                                                                   |                                                                                                                                                                                                               |
| `JASPER_DEFAULT_WRITE_ACCESS`                  | Additional write access qualified tags to apply to all users.                                                                  |                                                                                                                                                                                                               |
| `JASPER_DEFAULT_TAG_READ_ACCESS`               | Additional tag read access qualified tags to apply to all users.                                                               |                                                                                                                                                                                                               |
| `JASPER_DEFAULT_TAG_WRITE_ACCESS`              | Additional tag write access qualified tags to apply to all users.                                                              |                                                                                                                                                                                                               |
| `JASPER_STORAGE`                               | Path to the folder to use for storage. Used by the backup system.                                                              | `/var/lib/jasper`                                                                                                                                                                                             |
| `JASPER_NODE`                                  | Path to node binary for running javascript deltas.                                                                             | `/usr/local/bin/node`                                                                                                                                                                                         |
| `JASPER_CACHE_API`                             | HTTP address of an instance where storage is enabled.                                                                          |                                                                                                                                                                                                               |
| `JASPER_SSH_CONFIG_NAMESPACE`                  | K8s namespace to write authorized_keys config map file to.                                                                     |                                                                                                                                                                                                               |
| `JASPER_SSH_CONFIG_MAP_NAME`                   | K8s config map name to write authorized_keys file to.                                                                          |                                                                                                                                                                                                               |
| `JASPER_SECURITY_CONTENT_SECURITY_POLICY`      | Set the CSP header.                                                                                                            | `"default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:"` |
| `JASPER_OVERRIDE_SERVER_EMAIL_HOST`            | Override the server email host.                                                                                                |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_MAX_SOURCES`           | Override the server max sources.                                                                                               |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_MOD_SEALS`             | Override the server mod seals.                                                                                                 |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_EDITOR_SEALS`          | Override the server editor seals.                                                                                              |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_WEB_ORIGINS`           | Override the server origins with web access.                                                                                   |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_MAX_REPL_ENTITY_BATCH` | Override the server maximum batch size for replicate controller.                                                               |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_SSH_ORIGINS`           | Override the server origins with SSH access.                                                                                   |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_MAX_PUSH_ENTITY_BATCH` | Override the server maximum batch size for push replicate.                                                                     |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_MAX_PUSH_ENTITY_BATCH` | Override the server maximum batch size for pull replicate.                                                                     |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_SCRIPT_SELECTORS`      | Override the server tags and origins that can run scripts. No wildcard origins.                                                |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_SCRIPT_WHITELIST`      | Override the server list of whitelisted script SHA-256 hashes.                                                                 |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_HOST_WHITELIST`        | Override the server list of whitelisted hosts.                                                                                 |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SERVER_HOST_BLACKLIST`        | Override the server list of blacklisted hosts.                                                                                 |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_MODE`                | Override the security mode for all origins.                                                                                    |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_CLIENT_ID`           | Override the security clientId for all origins.                                                                                |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_BASE64_SECRET`       | Override the security base64Secret for all origins.                                                                            |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_SECRET`              | Override the security secret for all origins.                                                                                  |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_JWKS_URI`            | Override the security jwksUri for all origins.                                                                                 |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_USERNAME_CLAIM`      | Override the security usernameClaim for all origins.                                                                           |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_DEFAULT_USER`        | Override the security defaultUser for all origins.                                                                             |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_TOKEN_ENDPOINT`      | Override the security tokenEndpoint for all origins.                                                                           |                                                                                                                                                                                                               |
| `JASPER_OVERRIDE_SECURITY_SCIM_ENDPOINT`       | Override the security scimEndpoint for all origins.                                                                            |                                                                                                                                                                                                               |
| `JASPER_HEAP`                                  | Set both max and initial heap size for the JVM. Only applies to the docker container.                                          | `512m`                                                                                                                                                                                                        |

### Multi-tenant
When run with the default settings, the local origin is set to `""`. This means all origins are visible.
If you change the local origin to something else, like `@other`, you can only see sub-origins, like `@other.one`.
You can change the local origin with a HTTP header to use the server in multi-tenant mode. If you login though a
reverse-proxy or gateway that sets the local origin back to `""` you will still be able to see all origins.
You can also run workers in their own origin as a sandbox.

### Profiles
Setting the active profiles is done through the `SPRING_PROFILES_ACTIVE` environment
variable. Multiple profiles can be activated by adding them all as a comma
separated list.

For production use the `prod` profile should be active. For testing, the `dev` profile will
enable additional logging.

To enable JWT Token Authentication activate the `jwt` profile.
Either set the `_config/security` template in the Origin receiving traffic:
```json
{
  "mode": "jwt",
  "clientId": "",
  "base64Secret": "",
  "secret": "",
  "jwksUri": "",
  "usernameClaim": "",
  "tokenEndpoint": "",
  "scimEndpoint": ""
}
```
or set the environment variable overrides:
- Set `JASPER_OVERRIDE_SECURITY_MODE` to either `jwt` or `jwks` 
- Set `JASPER_OVERRIDE_SECURITY_CLIENT_ID`
- For `jwt` set `JASPER_OVERRIDE_SECURITY_BASE64_SECRET`
- For `jwks` set `JASPER_OVERRIDE_SECURITY_JWKS_URI`

If your user management server supports SCIM, you can enable the `scim` profile to manage users.
Requires the `_config/security` clientId, secret, and scimEndpoint set. Or
`JASPER_OVERRIDE_SECURITY_CLIENT_ID`, `JASPER_OVERRIDE_SECURITY_BASE64_SECRET`, 
and `JASPER_OVERRIDE_SECURITY_SCIM_ENDPOINT`
environment variable.

The `storage` profile is required for backups, caches, or preloading static files. Use the `JASPER_STORAGE` environment
variable to change the location of the storage folder.

The `preload` profile lets you preload static files. Zip files in the preload folder
`$JASPER_STORAGE/default/preload`. If `$JASPER_LOCAL_ORIGIN` is set,
`$JASPER_STORAGE/$JASPER_LOCAL_ORIGIN/preload` is used.

The `scripts` profile enables server side scripting through the `plugin/delta` Plugin.

## Access Control
Jasper uses a combination of simple roles and Tag Based Access Control (TBAC). There are five
hierarchical roles which cover broad access control, Admin, Mod, Editor, User, and Viewer. The
Anonymous role is given to users who are not logged in.
Roles are hierarchical, so they include any permissions granted to a preceding role.
 * `ROLE_ANONYMOUS`: read access to public tags and Refs.
 * `ROLE_VIEWER`: logged in user. Can be given access to private tags and Refs.
 * `ROLE_USER`: can post refs. Has read/write access to their user tag.
 * `ROLE_EDITOR`: can add/remove public tags to any post they have read access to.
 * `ROLE_MOD`: can read/write any tag or ref except plugins and templates.
 * `ROLE_ADMIN`: complete access to origin and sub-origins. Root admin can access all origins Can read/write plugins
and templates, perform backups and restores.

Tags are used to provide fine-grained access to resources. For Refs, the list of tags are considered.
For Tags entities, their tag is considered.

The tag permissions are stored in the User entities:
 * Tag Read Access
   * Can read tag
   * Can add tag
 * Tag Write Access
   * Can edit tag Ext
 * Read Access (Refs and Tags)
   * Can read ref with tag
   * Can read tag
   * Can add tag
 * Write Access (Refs and Tags)
   * No public tags
   * Can write ref with tag
   * Can edit tag Ext

### Special URL Schemas

### Cache
URLs that have the `cache:` scheme represent items stored in a file cache.
Most URLs with a resource in a file cache have a standard `https:` scheme,
as they are just a cache of a resource that exists elsewhere.
When a file is pushed into the cache (such as a generated thumbnail), it is
generated a random `cache:<uuid>` URL.

#### Tag URLs
URLs that point to a tag, such as `tag:/history` ignore regular tagging access rules.
Instead, you can access this Ref if you can access the tag it points to.

#### User URLs
URLs that point to a user tag, such as `tag:/+user/chris` are always owned by the user.
These specials URLs can also be used to store per-plugin config data,
such as `tag:/+user/chris?url=tag:/plugin/kanban`.
Visibility of plugin setting can be set on a per-user, per-plugin basis.
For convenience, the user URL is used if a blank URL is passed to the tagging response controller.
This allows you to quickly ensure settings are initialized and fetch / edit Ref plugins and tags to read settings.
If a tag are passed, for example `plugin/kanban`, the default is the kanban user settings Ref: `tag:/+user/chris?url=tag:/plugin/kanban`.
If a blank URL and a blank tag are passed, the default is the generic user settings Ref: `tag:/+user/chris`.

### Special Tags
Some public tags have special significance:
 * `public`: everyone can read
 * `internal`: don't show in UI normally, count separately in metadata
 * `locked`: No edits allowed (tagging is allowed, but not removing plugin data)

### Multi-tenant
Users only have read-access to their own origin and sub-origins.
For example, if a tenant has origin `@test`, they can also read `@test.other`. As usual, writing to
origins other than your own is never allowed.

### Access Tokens
When running the system with JWT authentication, roles may be added as claims.  
For example:
```json
{
  "sub": "username",
  "auth": "ROLE_USER"
}
```

Note: The claim names may be changed with the `JASPER_USERNAME_CLAIM`
and `JASPER_AUTHORITIES_CLAIM` properties.

## Backup / Restore
Jasper has a built-in backup system for mods and/or admins. Regular users should instead replicate to a separate jasper instance.
In order to use the backup system, the `storage` profile must be active.

## Validation
When ingesting entities, Jasper performs the following validation:
 * Fields must not exceed their maximum length
 * URLS are valid according to the regex `(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))`
 * Tags are valid according to their respective prefix and the general tag regex `[_+]?[a-z0-9]+([./][a-z0-9]+)*`
 * If a Ref has plugins present, any plugin data must conform to the plugin's schema
 * If an Ext matches a template prefix, any config must conform to all matching templates merged schemas

Plugin and Template schemas are in JTD, which only validates the shape of the data. In addition
to total bytes of the entity, these are the only server side data validations performed. No security related
validations should be required on Ref or Ext data, so client side validation should be sufficient in most cases.
Error checking should be the first part of any script parsing user input as part of a workflow.
We always want to err on the side of accepting well-shaped data rather than rejecting it, as server validation
errors rejecting valid user input are infuriating and very common. Error correction can happen as a follow-up step
if the client validation was somehow circumvented.

## Metadata
Jasper uses metadata generation pre-compute graph connections without including it in the transmitted data model.
Jasper generates the following metadata in Refs:
 * List of responses: This is an inverse lookup of the Ref sources. Excludes any Refs with the internal tag.
 * List of internal responses: This is an inverse lookup of the Ref sources that include the internal tag.
 * List of plugin responses: If a plugin has enabled metadata generation, this will include a list of responses with that plugin.
 * Obsolete: flag set if another origin contains the newest version of this Ref

## Server Scripting
When the `scripts` profile is active, scripts may be attached to Refs with either the `plugin/delta` tag or the
`plugin/script` tag.
Only admin users may install scripts and they run with very few guardrails. A regular user may invoke the script
by tagging a Ref. The tagged ref will be serialized as UTF-8 JSON and passed to stdin. Environment variables will
include the API endpoint as `JASPER_API`. Return a non-zero error code to fail the script and attach an error log.
The script should by writing UTF-8 JSON to stdout of the form:

```json
{
  "ref": [],
  "ext": [],
  "user": [],
  "plugin": [],
  "template": []
}
```

These entities will either be created or updated, as necessary.

Adding the `+plugin/error` tag will prevent any further processing. Remove the `+plugin/error` tag to retry.
You can also attach any error logs for the user to see by replying to the delta with the `+plugin/log` tag. Logs should
be tagged `internal` to prevent clutter, and should match the visibility of the parent delta (`public` or not) with the
same owner so the user can clear the logs as desired.

### Delta Scripts
Any Refs with a `plugin/delta` tag will run the attached script when modified.

You can use this to mark the input Ref as completed by either:
1. Removing the `plugin/delta` tag
2. Adding a `+plugin/delta` Plugin response

Right now only JavaScript scripts are supported. Here are examples that reply in all uppercase:

#### Remove the `plugin/delta` tag:
Use this approach when a script could be run multiple times to create multiple outputs.
```javascript
const whatPlugin = {
  tag: 'plugin/delta/what',
  config: {
    timeoutMs: 30_000,
    language: 'javascript',
    // language=JavaScript
    script: `
      const ref = JSON.parse(require('fs').readFileSync(0, 'utf-8'));
      const louderRef = {
        url: 'yousaid:' + ref.url,
        sources: [ref.url],
        comment: ref.comment.toUpperCase(),
      };
      louderRef.tags = ref.tags = ref.tags.filter(t => t !== 'plugin/delta/what' && !t.startsWith('plugin/delta/what/'));
      console.log(JSON.stringify({
        ref: [ref, louderRef],
      }));
    `,
  },
};
```

#### Add the `+plugin/delta` Plugin response:
This is the recommended approach as it does need to modify existing Refs and
is less likely for a bug to cause an infinite loop.
```javascript
const whatPlugin = {
  tag: 'plugin/delta/what',
  config: {
    timeoutMs: 30_000,
    language: 'javascript',
    // language=JavaScript
    script: `
      const ref = JSON.parse(require('fs').readFileSync(0, 'utf-8'));
      const louderRef = {
        url: 'yousaid:' + ref.url,
        sources: [ref.url],
        comment: ref.comment.toUpperCase(),
        tags: ['+plugin/delta/what']
      };
      console.log(JSON.stringify({
        ref: [louderRef],
      }));
    `,
  },
};
const whatPluginSignature = {
  tag: '+plugin/delta/what',
  generateMetadata: true,
};
```

### Cron scripts
Any Refs with a `plugin/script` tag will run the attached script when the `+plugin/cron` tag is also present.
The `+plugin/cron` tag contains plugin data with a default interval of 15 minutes:
```json
{
  "interval": "PT15M"
}
```

When the `+plugin/cron` tag is present the script will be run repeatedly at the interval specified. Removing the
`+plugin/cron` tag will disable the script.

You can use this to mark the input Ref as completed by either:
1. Removing the `plugin/delta` tag
2. Adding a `+plugin/delta` Plugin response

#### Example
Here is a script that outputs the current time:
```javascript
const timePlugin = {
  tag: 'plugin/script/time',
  config: {
    timeoutMs: 30_000,
    language: 'javascript',
    // language=JavaScript
    script: `
      const uuid = require('uuid');
      const ref = JSON.parse(require('fs').readFileSync(0, 'utf-8'));
      const timeRef = {
        url: 'comment:' + uuid.v4(),
        sources: [ref.url],
        comment: '' + new Date(),
        tags: ['public', 'time']
      };
      console.log(JSON.stringify({
        ref: [timeRef],
      }));
    `,
  },
};
```

## RSS / Atom Scraping
TODO: make this a mod `plugin/script/feed` and remove it from the server
The `plugin/feed` can be used to scrape RSS / Atom feeds. The `+plugin/cron` tag is used to set
the scraping interval. If no `+plugin/cron` is added the feed is considered disabled.
Although plugin fields are determined dynamically, the following fields are checked by the
scraper:
```json
{
  "optionalProperties": {
    "addTags": { "elements": { "type": "string" } },
    "disableEtag": { "type": "boolean" },
    "etag": { "type": "string" },
    "stripQuery": { "type": "boolean" },
    "scrapeWebpage": { "type": "boolean" },
    "scrapeDescription": { "type": "boolean" },
    "scrapeContents": { "type": "boolean" },
    "scrapeAuthors": { "type": "boolean" },
    "scrapeThumbnail": { "type": "boolean" },
    "scrapeAudio": { "type": "boolean" },
    "scrapeVideo": { "type": "boolean" },
    "scrapeEmbed": { "type": "boolean" }
  }
}
```

**Add Tags:** Tags to apply to any Refs created by this feed.  
**Disable Etag:** Don't use etag headers to skip unchanged feeds.  
**Strip Query:** Remove query (HTTP search field) from any scraped links.  
**Scrape Webpage:** Scrape the web-page directly instead.  
**Scrape Description:** Use description field in the feed for the Ref comment field.    
**Scrape Contents:** Use contents field in the feed for the Ref comment field.  
**Scrape Authors:** Use authors field in the feed to add an authors line at the bottom of the Ref comment field.  
**Scrape Thumbnail:** Add a `plugin/thumbnail` Plugin to the Ref with attached feed media.  
**Scrape Audio:** Add a `plugin/audio` Plugin to the Ref with attached feed media.  
**Scrape Video:** Add a `plugin/video` Plugin to the Ref with attached feed media.  
**Scrape Embed:** Add a `plugin/embed` tag to the Ref to load oEmbed.  

The `plugin/feed` will be set as a source for all scraped Refs. If the published date of the new entry is prior to the published date of the
`plugin/feed` it will be skipped.

## Remote Origin
The `+plugin/origin` tag marks a Ref as a Remote Origin and associates it with a local alias. These may be either pulled from or pushed to.
```json
{
  "optionalProperties": {
    "local": { "type": "string" },
    "remote": { "type": "string" }
  }
}
```

**Local:** Local alias for the remote origin.  
**Remote:** Remote origin to query, or blank for the default.  

## Replicating Remote Origin
The `+plugin/origin/pull` tag can be used to replicate remote origins. Since this plugin
extends `+plugin/origin`, we already have the `local` and `remote`
fields set.
```json
{
  "properties": {
    "pullInterval": { "type": "string" }
  },
  "optionalProperties": {
    "query": { "type": "string" },
    "proxy": { "type": "string" },
    "lastPull": { "type": "string" },
    "batchSize": { "type": "int32" },
    "generateMetadata": { "type": "boolean" },
    "validatePlugins": { "type": "boolean" },
    "validateTemplates": { "type": "boolean" },
    "addTags": { "elements": { "type": "string" } },
    "removeTags": { "elements": { "type": "string" } }
  }
}
```

**Query:** Restrict results using a query. Can not use qualified tags as replication only works on a single origin at
a time. If you want to combine multiple origins into one, create multiple `+plugin/origin` Refs.  
**Proxy:** Alternate URL to replicate from.  
**Last Pull:** The time this origin was last replicated.  
**Pull Interval:** The time interval to replicate this origin. Use ISO 8601 duration format.  
**Batch Size:** The max number of entities of each type to pull each interval.  
**Generate Metadata:** Flag to enable, disable metadata generation.  
**Validate Plugins:** Flag to enable, disable plugin validation.  
**Validate Templates:** Flag to enable, disable template validation.  
**Validation Origin:** Origin to get plugin and templates for validation.  
**Add Tags:** Tags to apply to any Refs replicated from this origin.  
**Remove Tags:** Tags to remove from any Refs replicated from this origin.  

## Pushing to a Remote Origin
The `+plugin/origin/push` tag can be used to replicate remote origins. Since this plugin
extends `+plugin/origin`, we already have the `local` and `remote`
fields set.
```json
{
  "properties": {
    "pushInterval": { "type": "string" }
  },
  "optionalProperties": {
    "query": { "type": "string" },
    "proxy": { "type": "string" },
    "lastPush": { "type": "string" },
    "batchSize": { "type": "int32" },
    "writeOnly": { "type": "boolean" },
    "lastModifiedRefWritten": { "elements": { "type": "string" } },
    "lastModifiedExtWritten": { "elements": { "type": "string" } },
    "lastModifiedUserWritten": { "elements": { "type": "string" } },
    "lastModifiedPluginWritten": { "elements": { "type": "string" } },
    "lastModifiedTemplateWritten": { "elements": { "type": "string" } }
  }
}
```

**Query:** Restrict push using a query. Can not use qualified tags as replication only works on a single origin at
a time. If you want to combine multiple origins into one, create multiple `+plugin/origin` Refs.  
**Proxy:** Alternate URL to push to.  
**Last Push:** The time this origin was last pushed to.  
**Pull Interval:** The time interval to replicate this origin. Use ISO 8601 duration format.  
**Batch Size:** The max number of entities of each type to pull each interval.  
**Write Only:** Do not query remote for last modified cursor, just use saved cursor.  
**Last Modified Ref Written:** Modified date of last Ref pushed.    
**Last Modified Ext Written:** Modified date of last Ext pushed.    
**Last Modified User Written:** Modified date of last User pushed.    
**Last Modified Plugin Written:** Modified date of last Plugin pushed.    
**Last Modified Template Written:** Modified date of last Template pushed.    

## Random Number Generator

The `plugin/rng` tag can be used to generate random numbers. Random numbers are generated whenever editing, creating or
pushing a Ref replaces an existing Ref of a different origin. When a new random number is generated it is represented in
hex
in the tag `+plugin/rng/6d7eb8ebb38a47d29c6a6cbc9156a1a3`, for example. When replicated, random numbers will not be
overwritten so that spectators may verify the results. Editing of a Ref that is already the latest
version across all origins will preserve the existing random number or lack thereof. This ensures random numbers can't
be farmed, as you cannot generate a new number without cooperation from another origin.

When delegating rng to a trusted server, users push their updates to that server and replicate the results.  
When playing on mutually replicating servers, each server is trusted to generate their own rng.

## Release Notes
* [v1.2](./docs/release-notes/jasper-1.2.md)
* [v1.1](./docs/release-notes/jasper-1.1.md)
* [v1.0](./docs/release-notes/jasper-1.0.md)

## Developing
Run a dev server with `docker compose up`.  
Run a supporting dev database and cache with `docker compose up db redis -d`.

### Build

Run `docker build -t jasper .` to build the project.

### Running unit tests

Run `docker build --target=test -t jasper-tests .` to build the tests.  
Run `docker run -it jasper-tests` to execute the unit tests.

### Running end-to-end tests

See [Jasper-UI Cypress Tests](https://github.com/cjmalloy/jasper-ui/actions/workflows/cypress.yml).
