---
layout: default
---

# Errors

Every error returned by the Jasper API is a
[Problem Details](https://www.rfc-editor.org/rfc/rfc9457) JSON body with the content type
`application/problem+json`. Clients should branch on the stable error code in `message`,
use `type` to look up the category on this page, and show `detail` to users.

## Response format

```json
{
  "type": "https://cjmalloy.github.io/jasper/docs/errors.html#conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Ref already modified",
  "message": "error.modified",
  "path": "/api/v1/ref"
}
```

| Field         | Description                                                                                                                        |
|---------------|------------------------------------------------------------------------------------------------------------------------------------|
| `type`        | Category URI. Links to the matching section of this page.                                                                          |
| `title`       | Short, human readable summary of the HTTP status (or the `@ResponseStatus` reason).                                                |
| `status`      | HTTP status code. Always matches the response status.                                                                              |
| `detail`      | Human readable explanation of this occurrence. In the `prod` profile, 5xx details, SQL and class or package names are replaced.     |
| `message`     | Stable error code. Use this for translations and client logic. Falls back to `error.http.<status>` for unmapped exceptions.        |
| `path`        | Request path that caused the error.                                                                                                |
| `fieldErrors` | Only for `error.validation`: list of `objectName`, `field` and `message` for each invalid field.                                   |
| `tag`         | Only for [plugin](#plugin) and [template](#template) errors: the Plugin or Template tag that failed validation.                    |
| `reason`      | Only for [plugin](#plugin) and [template](#template) errors: why validation failed. See [Validation reasons](#validation-reasons). |
| `errors`      | Only for [plugin](#plugin) and [template](#template) errors: list of [field errors](#field-errors), at most 50.                    |
| `truncated`   | Only for [plugin](#plugin) and [template](#template) errors: `true` if more than 50 field errors were found.                       |

When an exception wraps another exception, the outermost exception with a mapping in the cause chain
determines the code and category. For example, a wrapped `ConcurrencyFailureException` still
returns `error.optimisticLock`.

## General

Unmapped exceptions. The code is `error.http.<status>`, for example `error.http.500`.

| Code               | Status | Exception     | Meaning                                       | Client action                                       |
|--------------------|--------|---------------|-----------------------------------------------|-----------------------------------------------------|
| `error.http.<NNN>` | any    | any unmapped  | Unexpected error without a specific code.     | Show `detail`. Retry later or report it if it is 5xx. |

## Request

The HTTP request itself could not be processed.

| Code                         | Status | Exception                                                                  | Meaning                                                 | Client action                                      |
|------------------------------|--------|----------------------------------------------------------------------------|---------------------------------------------------------|----------------------------------------------------|
| `error.messageNotReadable`   | 400    | `HttpMessageNotReadableException`                                          | The request body is missing or is not valid JSON.       | Fix the request body.                              |
| `error.missingParameter`     | 400    | `MissingServletRequestParameterException`, `MissingServletRequestPartException` | A required query parameter or multipart part is missing. | Add the parameter named in `detail`.               |
| `error.typeMismatch`         | 400    | `MethodArgumentTypeMismatchException`                                      | A parameter could not be converted to the required type. | Fix the parameter value named in `detail`.         |
| `error.methodNotSupported`   | 405    | `HttpRequestMethodNotSupportedException`                                   | The HTTP method is not supported for this path.          | Use a method from the `Allow` header.              |
| `error.mediaTypeNotSupported`| 415    | `HttpMediaTypeNotSupportedException`                                       | The `Content-Type` is not supported.                     | Use a supported `Content-Type`, usually JSON.      |
| `error.invalidQuery`        | 400    | `InvalidQueryException`                                                    | The tag query could not be parsed.                       | Fix the query syntax described in `detail`.        |

## Constraint

The request was well formed but violates a constraint.

| Code                 | Status | Exception                         | Meaning                                                      | Client action                                    |
|----------------------|--------|-----------------------------------|--------------------------------------------------------------|--------------------------------------------------|
| `error.validation`   | 400    | `MethodArgumentNotValidException` | Bean validation failed, see `fieldErrors`.                   | Fix each field listed in `fieldErrors`.          |
| `error.invalidPatch` | 400    | `InvalidPatchException`           | A JSON Patch or Merge Patch could not be applied.            | Fix the patch document.                          |
| `error.invalidPush`  | 400    | `InvalidPushException`            | A replication push contains invalid data.                    | Fix the pushed entities and push again.          |

## Plugin

Plugin data on a Ref does not match the Plugin schema, or is not allowed. Plugin errors include
`tag`, `reason`, `errors` and `truncated` so clients can highlight exactly which field failed.
`detail` is a one line summary for display.

| Code                   | Status | Exception                       | Meaning                                                              | Client action                                       |
|------------------------|--------|---------------------------------|----------------------------------------------------------------------|-----------------------------------------------------|
| `error.invalidPlugin`  | 400    | `InvalidPluginException`        | Plugin data is not allowed, is missing its tag, or fails validation. | Fix the plugin data using `reason` and `errors`.    |
| `error.invalidUserUrl` | 400    | `InvalidPluginUserUrlException` | A `plugin/user` Ref does not have a valid user URL and source.       | Use the user URL for the source and a user tag.     |

Example: `{ "plugin/test": { "age": "thirty" } }` for a Plugin with the schema
`{ "properties": { "name": { "type": "string" }, "age": { "type": "uint32" } } }`:

```json
{
  "type": "https://cjmalloy.github.io/jasper/docs/errors.html#plugin",
  "title": "Bad Request",
  "status": 400,
  "detail": "plugin/test: age: expected uint32; name: required",
  "message": "error.invalidPlugin",
  "path": "/api/v1/ref",
  "tag": "plugin/test",
  "reason": "schema",
  "errors": [
    { "path": "/age", "schemaPath": "/properties/age/type", "code": "type", "expected": "uint32", "message": "age: expected uint32" },
    { "path": "/name", "schemaPath": "/properties/name", "code": "missing", "expected": "string", "message": "name: required" }
  ],
  "truncated": false
}
```

Example: a `plugin/user` Ref without a source:

```json
{
  "type": "https://cjmalloy.github.io/jasper/docs/errors.html#plugin",
  "title": "Bad Request",
  "status": 400,
  "detail": "plugin/user: requires exactly one source",
  "message": "error.invalidUserUrl",
  "path": "/api/v1/ref",
  "tag": "plugin/user",
  "reason": "userUrl.sources",
  "errors": [],
  "truncated": false
}
```

### Field errors

Each entry in `errors` describes one field. Submitted values are never echoed back.

| Field        | Description                                                                                                              |
|--------------|--------------------------------------------------------------------------------------------------------------------------|
| `path`       | [JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901) into the submitted plugin data or config, e.g. `/age` or `/a/b/0`. |
| `schemaPath` | JSON Pointer into the [JTD](https://jsontypedef.com/) schema, e.g. `/properties/age/type`. `null` for `config` errors.   |
| `code`       | Kind of failure, see below.                                                                                              |
| `expected`   | What the schema expects, if known: a JTD type such as `uint32`, `object` or `array`, or the comma separated allowed values. |
| `message`    | Short readable message, e.g. `age: expected uint32`, `name: required` or `extra: not allowed`.                           |

| Code            | Meaning                                                                              | `expected`                          |
|-----------------|--------------------------------------------------------------------------------------|-------------------------------------|
| `missing`       | A required property is absent. `path` points to the missing property.                | Type of the missing property        |
| `unexpected`    | A property is not in the schema.                                                     |                                     |
| `type`          | The value has the wrong type.                                                        | JTD type, e.g. `uint32` or `object` |
| `enum`          | The value is not one of the allowed values.                                          | Allowed values, e.g. `a, b`         |
| `elements`      | An array was expected.                                                               | `array`                             |
| `values`        | An object of values was expected.                                                    | `object`                            |
| `discriminator` | The discriminator property is missing, not a string, or not one of the mapping keys. | Mapping keys                        |
| `nullable`      | The value is `null` but the schema is not nullable.                                  | Expected type                       |
| `invalid`       | Only for `config` errors: the value could not be read.                               |                                     |

### Validation reasons

`reason` explains why a plugin or template failed validation:

| Reason            | Meaning                                                                                                 | `errors`          |
|-------------------|---------------------------------------------------------------------------------------------------------|-------------------|
| `schema`          | The data does not match the schema.                                                                     | One per field     |
| `untagged`        | Plugin data was sent without its tag.                                                                   | Empty             |
| `schemaless`      | Plugin data or Ext config was sent for a Plugin or Template without a schema.                           | Empty             |
| `maxDepth`        | The data is nested deeper than the maximum schema depth (32).                                           | Empty             |
| `invalidDefaults` | The Plugin or Template defaults fail its own schema. This is a server config bug, not a user error.     | Defaults errors   |
| `userUrl.sources` | Only `error.invalidUserUrl`: a `plugin/user` Ref must have exactly one source.                          | Empty             |
| `userUrl.userTag` | Only `error.invalidUserUrl`: a `plugin/user` Ref must have a user tag.                                  | Empty             |
| `userUrl.prefix`  | Only `error.invalidUserUrl`: the Ref URL must start with the user URL for the source and user tag.      | Empty             |
| `config`          | Only `error.invalidTemplate`: `_config/server` or `_config/security` config could not be read.          | The bad field     |

Plugin and template errors are shown in full in every profile, including `prod`.

## Template

Ext config does not match the Template schema, or Template config for `_config/server` or
`_config/security` could not be read. Template errors use the same `tag`, `reason`, `errors` and
`truncated` fields as [plugin](#plugin) errors, with the same [field errors](#field-errors) and
[validation reasons](#validation-reasons).

| Code                    | Status | Exception                  | Meaning                                        | Client action                                    |
|-------------------------|--------|----------------------------|------------------------------------------------|--------------------------------------------------|
| `error.invalidTemplate` | 400    | `InvalidTemplateException` | Template config fails validation.              | Fix the config using `reason` and `errors`.      |

Example: an Ext with the config `{ "kind": "c" }` for a Template with the schema
`{ "properties": { "age": { "type": "uint32" } }, "optionalProperties": { "kind": { "enum": ["a", "b"] } } }`:

```json
{
  "type": "https://cjmalloy.github.io/jasper/docs/errors.html#template",
  "title": "Bad Request",
  "status": 400,
  "detail": "test: age: required; kind: expected one of a, b",
  "message": "error.invalidTemplate",
  "path": "/api/v1/ext",
  "tag": "test",
  "reason": "schema",
  "errors": [
    { "path": "/age", "schemaPath": "/properties/age", "code": "missing", "expected": "uint32", "message": "age: required" },
    { "path": "/kind", "schemaPath": "/optionalProperties/kind/enum", "code": "enum", "expected": "a, b", "message": "kind: expected one of a, b" }
  ],
  "truncated": false
}
```

Example: `_config/server` with `{ "maxSources": "many" }`:

```json
{
  "type": "https://cjmalloy.github.io/jasper/docs/errors.html#template",
  "title": "Bad Request",
  "status": 400,
  "detail": "_config/server: maxSources: expected int",
  "message": "error.invalidTemplate",
  "path": "/api/v1/template",
  "tag": "_config/server",
  "reason": "config",
  "errors": [
    { "path": "/maxSources", "schemaPath": null, "code": "type", "expected": "int", "message": "maxSources: expected int" }
  ],
  "truncated": false
}
```

## Access

Authentication or authorization failed.

| Code                 | Status | Exception                 | Meaning                                         | Client action                                         |
|----------------------|--------|---------------------------|-------------------------------------------------|-------------------------------------------------------|
| `error.unauthorized` | 401    | `AuthenticationException` | Credentials are missing or invalid.             | Log in again or send a valid token.                   |
| `error.accessDenied` | 403    | `AccessDeniedException`   | The user may not read or write this resource.   | Request access, or don't show the action to the user. |

Failures raised by the security filter chain, such as a missing or invalid CSRF token, use the same codes.

## Missing

The requested resource does not exist.

| Code             | Status | Exception                  | Meaning                                       | Client action                                 |
|------------------|--------|----------------------------|-----------------------------------------------|-----------------------------------------------|
| `error.notFound` | 404    | `NotFoundException`        | The entity does not exist or is not visible.  | Check the URL, tag or origin.                 |
| `error.notFound` | 404    | `NoResourceFoundException` | No API endpoint or static resource matches.   | Check the request path.                       |

## Duplicate

A unique key is already taken.

| Code                          | Status | Exception                        | Meaning                                                   | Client action                                       |
|-------------------------------|--------|----------------------------------|-----------------------------------------------------------|-----------------------------------------------------|
| `error.duplicateTag`          | 409    | `DuplicateTagException`          | The tag is already in use.                                | Choose a different tag.                             |
| `error.duplicateModifiedDate` | 409    | `DuplicateModifiedDateException` | Another entity has the same modified date in this origin. | Retry the request.                                  |
| `error.duplicateKey`          | 409    | `DuplicateKeyException`          | A database unique constraint was violated.                | Choose a different key, or fetch the existing one.  |

## Conflict

The request conflicts with the current state of the resource.

| Code                   | Status | Exception                         | Meaning                                                        | Client action                                         |
|------------------------|--------|-----------------------------------|----------------------------------------------------------------|-------------------------------------------------------|
| `error.optimisticLock` | 409    | `ConcurrencyFailureException`     | The entity was changed concurrently.                           | Reload the entity, reapply the change and save again. |
| `error.alreadyExists`  | 409    | `AlreadyExistsException`          | An entity with this key already exists.                        | Update the existing entity instead.                   |
| `error.modified`       | 409    | `ModifiedException`               | The entity was modified since the `modified` date you sent.    | Reload the entity, reapply the change and save again. |
| `error.userTagInUse`   | 409    | `UserTagInUseException`           | The user tag is already linked to another user.                | Choose a different user tag.                          |
| `error.dataIntegrity`  | 409    | `DataIntegrityViolationException` | A database constraint was violated.                            | Fix the data described in `detail` and retry.         |

## User

User account and session errors.

| Code                       | Status | Exception                     | Meaning                                            | Client action                                  |
|----------------------------|--------|-------------------------------|----------------------------------------------------|------------------------------------------------|
| `error.freshLogin`         | 403    | `FreshLoginException`         | The action requires a recent login.                | Log in again and retry.                        |
| `error.deactivateSelf`     | 403    | `DeactivateSelfException`     | Users may not deactivate their own account.        | Ask another admin to deactivate the account.   |
| `error.invalidUserProfile` | 400    | `InvalidUserProfileException` | The user profile is invalid.                       | Fix the profile fields described in `detail`.  |

## Origin

The operation is not allowed for this origin.

| Code                    | Status | Exception                             | Meaning                                                | Client action                                   |
|-------------------------|--------|---------------------------------------|--------------------------------------------------------|-------------------------------------------------|
| `error.originForbidden` | 403    | `OperationForbiddenOnOriginException` | The origin is not whitelisted for this operation.      | Use a whitelisted origin.                       |
| `error.pullLocal`       | 403    | `PullLocalException`                  | Remotes can't be pulled into the local origin.         | Pull into a nested origin instead.              |

## Script

Running a plugin script failed.

| Code                    | Status | Exception                 | Meaning                                         | Client action                                         |
|-------------------------|--------|---------------------------|-------------------------------------------------|-------------------------------------------------------|
| `error.script`          | 500    | `ScriptException`         | The script exited with an error.                | Check the script logs, fix the script and retry.      |
| `error.untrustedScript` | 403    | `UntrustedScriptException`| The script hash is not in the trusted whitelist. | Ask an admin to whitelist the script.                |

## Date

Dates are inconsistent.

| Code                | Status | Exception              | Meaning                                         | Client action                                      |
|---------------------|--------|------------------------|-------------------------------------------------|----------------------------------------------------|
| `error.publishDate` | 409    | `PublishDateException` | A source must be published before its response. | Fix the published dates of the source or response. |

## Protocol

A tunnel or scrape protocol is not supported or failed.

| Code                   | Status | Exception                  | Meaning                                   | Client action                                 |
|------------------------|--------|----------------------------|-------------------------------------------|-----------------------------------------------|
| `error.invalidTunnel`  | 400    | `InvalidTunnelException`   | The SSH tunnel config is invalid.         | Fix the tunnel config described in `detail`.  |
| `error.tunnelTimeout`  | 408    | `RetryableTunnelException` | The SSH tunnel could not be opened in time. | Retry later.                                |
| `error.scrapeProtocol` | 400    | `ScrapeProtocolException`  | The URL protocol can't be scraped.        | Use an `http` or `https` URL.                 |

## Size

The request is too large.

| Code               | Status | Exception                        | Meaning                                       | Client action                                   |
|--------------------|--------|----------------------------------|-----------------------------------------------|-------------------------------------------------|
| `error.tooLarge`   | 413    | `TooLargeException`              | Too many entities were requested.             | Request a smaller page or batch.                |
| `error.tooLarge`   | 413    | `MaxUploadSizeExceededException` | The upload exceeds the maximum upload size.   | Upload a smaller file.                          |
| `error.maxSources` | 400    | `MaxSourcesException`            | The Ref has more sources than allowed.        | Remove sources until under the configured max.  |

## Unavailable

The server can't handle the request right now.

| Code                 | Status | Exception               | Meaning                                         | Client action                           |
|----------------------|--------|-------------------------|-------------------------------------------------|-----------------------------------------|
| `error.notAvailable` | 503    | `NotAvailableException` | A required service or feature is not available. | Retry later or enable the feature.      |
