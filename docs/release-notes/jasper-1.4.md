# v1.4 Release Notes

This release includes changes accumulated since v1.3.0. Features marked *Experimental* are opt-in and may change in 1.4.x patch releases as they're polished. Only the latest minor release line gets fixes.

## Server

* Added experimental SQLite support, so Jasper can run without PostgreSQL by enabling the `sqlite` profile. The database is stored at `$JASPER_STORAGE/db.sqlite` (default `/var/lib/jasper`). Bulk metadata regeneration falls back to background backfill on SQLite.
* Added experimental cloud storage using Google Cloud Storage and S3 or S3-compatible services such as MinIO and Cloudflare R2. Enable `storage` plus `gcs` and/or `s3`. Configure `storage`, `storageBucket`, and `storageRoutes` in the `_config/server` template, or use the `JASPER_OVERRIDE_SERVER_STORAGE*` environment variables. Routes can be set by tenant and namespace. See the [README storage details and examples](../../README.md#profiles).
* Added CDN support for cached video. Storage routes with a `cdnBaseUrl` can serve cached HLS (M3U8) segments directly from the CDN instead of the proxy.
* Backup and restore now skip deleted items (tombstones) by default. Set `tombstones: true` in backup options to include them.
* Backups are only published after they have been fully written, so a failed backup no longer leaves a partial zip behind.
* Internal Refs now have their published dates corrected automatically against their first two sources when possible.
* Improved cache existence checks and origin lookup for replication.
* Improved source metadata updates, backfill, and filtering of obsolete Refs.
* Added JSON support for using a non-object value as plugin data and improved plugin data handling when patching Refs.
* Added audio and video support to the HTML sanitizer.
* Added sorting Refs by tag value: `tags->plugin/title` (text), `tags->plugin/progress:num` (numeric, e.g. `37` from `plugin/progress/37/100`) and `tags->plugin/duration:dur` (ISO-8601 durations such as `pt10m25s`, sorted by length). See the [README sorting section](../../README.md#sorting).
* Added `hotTags` to the `_config/index` template to index tag value sorts on PostgreSQL, e.g. `{"hotTags": ["plugin/duration:dur"]}`. Removing a hot tag drops its index.
* Added filtering Refs by tag value with `tagBefore` and `tagAfter`, e.g. `tagBefore=tags->plugin/progress:num/50` or `tagAfter=tags->plugin/duration:dur/pt1h`. See the [README filtering section](../../README.md#filtering-by-value).
* Added filtering Refs by plugin data with `pluginBefore` and `pluginAfter`, using plugin sort columns, e.g. `pluginAfter=plugins->_plugin/cache->contentLength:num/1000`. See the [README filtering section](../../README.md#filtering-by-value).
* Added `hotPlugins` to the `_config/index` template to index plugin filters and sorts on PostgreSQL, e.g. `{"hotPlugins": ["plugin/test->size:num"]}`. Each hot plugin builds one index that serves its filters and both sort directions. Removing a hot plugin drops its index.
* JSONB sorts (`plugins->…`, `config->…`, `external->…`, `defaults->…`, `schema->…`, `metadata->newResponse` and `metadata->newReaction`) now sort missing values first ascending and last descending, instead of as `''` or `0`. Missing or non-numeric `:num` values now sort before negative numbers instead of with `0`. Metadata counts still count missing as `0`. Value filters use the same expression, so a filter keeps exactly the matching part of the sort order.
* Added `newResponse` and `newReaction` to the `_config/index` template (both enabled by default) to index sorting Refs by `metadata->newResponse` and `metadata->newReaction`.
* Added `hotResponses` and `hotRemoteResponses` to the `_config/index` template to index response counts on PostgreSQL, e.g. `{"hotRemoteResponses": ["plugin/comment"]}`, for sorting by `metadata->plugins->plugin/comment` or `metadata->remotePlugins->plugin/comment` and filtering with `pluginResponse` or `remotePluginResponse`. Removing a plugin drops its index.
* Added sorting Refs by `metadata->remotePlugins->{plugin}` (response counts from all origins), and the `remotePluginResponse` and `noRemotePluginResponse` filters. Response counts now sort numerically without the `:num` suffix.
* Added response count thresholds to `pluginBefore` and `pluginAfter`: `metadata->plugins->{plugin}[:num]/{count}` and `metadata->remotePlugins->{plugin}[:num]/{count}`, e.g. `pluginAfter=metadata->remotePlugins->plugin/user/read/10` for Refs read by more than 10 users. They use the `hotResponses` / `hotRemoteResponses` indexes. Filters on private plugins need read access to the plugin, like sorts.
* Added `newResponseBefore` / `newResponseAfter` and `newReactionBefore` / `newReactionAfter` filters, using the `newResponse` and `newReaction` indexes, e.g. `newResponseAfter=<now - 1 day>` for Refs active in the last day.
* `responseBefore` / `responseAfter` are now aliases of `newResponseBefore` / `newResponseAfter` and compare `metadata->newResponse` instead of `metadata->modified`. Prefer the new names.
* Removed the `metadata->modified` sort, use `metadata->newResponse` instead.
* Added `created` to the `_config/index` template (enabled by default) to index the default `created` sort and the `createdBefore` / `createdAfter` filters.
* The `userResponse` and `noUserResponse` filters now look up the current user's user URL Refs (by URL prefix) instead of scanning `metadata->userUrls`, so "unread" and "saved" lists are indexed. Added `urlPrefix` to the `_config/index` template (enabled by default, PostgreSQL only) for the prefix lookup. Results now follow the user URL Refs directly, so they no longer depend on metadata being up to date.
* Added `configBefore` / `configAfter` filters to the Ext, User, Plugin and Template page endpoints (and the Ext count endpoint), e.g. `configAfter=config->pinned:len/0` for Exts, `configAfter=external->ids:len/1` for Users or `configAfter=config->version:num/3` for Plugins. These are not indexed.
* Only mods can filter or sort Users by `external`, since it is hidden from other users. Non-mods get `403 Forbidden` for `configBefore` / `configAfter` on Users, and `external->` sorts are ignored.
* Sorting by plugin data with `:num` or `:len` no longer fails on PostgreSQL when a value is not a number or not an array; those values sort as `0`.
* Request parameters that fail validation (for example a `query` with invalid characters) now return `400 Bad Request` instead of `500 Internal Server Error`.

## Upgrading

* Before starting v1.4.0 with PostgreSQL, clear the stored Liquibase checksum for the changed schema changeset: `UPDATE DATABASECHANGELOG SET MD5SUM = NULL;`. Liquibase will recalculate the checksum on startup and re-run the SQL function changeset, which adds a small `instr()` function and the `tag_value`, `tag_value_num` and `tag_value_dur` functions used for tag value sorting.
* If you rely on deleted items being included in backups, set `tombstones: true` in your backup options.
* The SCIM integration no longer depends on a third-party SDK. No configuration changes are needed.
* Cloud storage needs credentials: GCS uses Application Default Credentials (e.g. GKE Workload Identity), and S3 uses the default AWS credential chain (e.g. EKS IRSA). Set `APPLICATION_STORAGE_S3_REGION` / `APPLICATION_STORAGE_S3_ENDPOINT` as needed, and `APPLICATION_STORAGE_TMP_DIR` for zip staging space.
