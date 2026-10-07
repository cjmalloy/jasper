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

## Constraint

The request was well formed but violates a constraint.

| Code                 | Status | Exception                         | Meaning                                                      | Client action                                    |
|----------------------|--------|-----------------------------------|--------------------------------------------------------------|--------------------------------------------------|
| `error.validation`   | 400    | `MethodArgumentNotValidException` | Bean validation failed, see `fieldErrors`.                   | Fix each field listed in `fieldErrors`.          |
| `error.invalidPatch` | 400    | `InvalidPatchException`           | A JSON Patch or Merge Patch could not be applied.            | Fix the patch document.                          |
| `error.invalidPush`  | 400    | `InvalidPushException`            | A replication push contains invalid data.                    | Fix the pushed entities and push again.          |

## Plugin

Plugin data does not match the Plugin schema. `detail` describes which field failed.

| Code                   | Status | Exception                       | Meaning                                                              | Client action                                       |
|------------------------|--------|---------------------------------|----------------------------------------------------------------------|-----------------------------------------------------|
| `error.invalidPlugin`  | 400    | `InvalidPluginException`        | Plugin data is not allowed, is missing its tag, or fails validation. | Fix the plugin data using the field in `detail`.    |
| `error.invalidUserUrl` | 400    | `InvalidPluginUserUrlException` | A `plugin/user` Ref does not have a valid user URL and source.       | Use the user URL for the source and a user tag.     |

## Template

Template config does not match the Template schema. `detail` describes which field failed.

| Code                    | Status | Exception                  | Meaning                                        | Client action                                    |
|-------------------------|--------|----------------------------|------------------------------------------------|--------------------------------------------------|
| `error.invalidTemplate` | 400    | `InvalidTemplateException` | Template config fails validation.              | Fix the config using the field in `detail`.      |

## Access

Authentication or authorization failed.

| Code                 | Status | Exception                 | Meaning                                         | Client action                                         |
|----------------------|--------|---------------------------|-------------------------------------------------|-------------------------------------------------------|
| `error.unauthorized` | 401    | `AuthenticationException` | Credentials are missing or invalid.             | Log in again or send a valid token.                   |
| `error.accessDenied` | 403    | `AccessDeniedException`   | The user may not read or write this resource.   | Request access, or don't show the action to the user. |

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

## Breaking changes

Compared to the previous release (`master` before this change):

* `type` URIs moved from `https://www.jhipster.tech/problem/...` to the categories on this page.
* `error.concurrencyFailure` was renamed to `error.optimisticLock`.
* Every code in the tables above other than `error.validation` previously returned the generic
  `error.http.<status>` code, for example `error.http.409` for `AlreadyExistsException` is now `error.alreadyExists`:
  `error.messageNotReadable`, `error.missingParameter`, `error.typeMismatch`, `error.methodNotSupported`,
  `error.mediaTypeNotSupported`, `error.invalidPatch`, `error.invalidPush`, `error.invalidPlugin`,
  `error.invalidUserUrl`, `error.invalidTemplate`, `error.unauthorized`, `error.accessDenied`, `error.notFound`,
  `error.duplicateTag`, `error.duplicateModifiedDate`, `error.duplicateKey`, `error.alreadyExists`,
  `error.modified`, `error.userTagInUse`, `error.dataIntegrity`, `error.freshLogin`, `error.deactivateSelf`,
  `error.invalidUserProfile`, `error.originForbidden`, `error.pullLocal`, `error.script`,
  `error.untrustedScript`, `error.publishDate`, `error.invalidTunnel`, `error.tunnelTimeout`,
  `error.scrapeProtocol`, `error.tooLarge`, `error.maxSources` and `error.notAvailable`.
* `DataIntegrityViolationException` and `DuplicateKeyException` return 409 instead of 500.
* `AuthenticationException` subclasses other than `BadCredentialsException` return 401 instead of 500.
* `title` falls back to the HTTP reason phrase instead of an empty string when `@ResponseStatus` has no reason.
