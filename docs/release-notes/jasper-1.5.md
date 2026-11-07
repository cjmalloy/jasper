# v1.5 Release Notes

This release includes changes accumulated since v1.4.0. Only the latest minor release line gets fixes.

## Server

* Replaced the per-field filter parameters with a single repeatable `filter` parameter on the Ref, Ext, User, Plugin and Template page and count endpoints, in the same `{sort column}/{before|after|equals}/{value}` format jasper-ui uses. This also replaces the `filterBefore`, `filterAfter` and `filterEquals` parameters from earlier 1.5 releases. Anything you can sort by, you can filter by: e.g. `filter=modified/after/2024-01-01T00:00:00Z`, `filter=tags->plugin/progress:num/before/50`, `filter=plugins->_plugin/cache->contentLength:num/after/1000`, `filter=metadata->remotePlugins->plugin/user/read/after/10` for Refs read by more than 10 users, or `filter=config->pinned:len/after/0` for Exts. Filters use the same expression as the sort, so the same indexes apply. Filters on private plugins or tags need read access, like sorts. See the [README filtering section](../../README.md#filtering).
* Removed `modifiedBefore`, `modifiedAfter`, `publishedBefore`, `publishedAfter`, `createdBefore`, `createdAfter`, `responseBefore`, `responseAfter`, `tagBefore`, `tagAfter`, `pluginBefore`, `pluginAfter`, `pluginResponse`, `noPluginResponse`, `remotePluginResponse` and `noRemotePluginResponse` from the page and count endpoints. Use the `filter` parameter instead:
  * `filter=modified/after/{date}` instead of `modifiedAfter={date}`
  * `filter=metadata->newResponse/after/{date}` instead of `responseAfter={date}`
  * `filter=tags->{tag}/after/{value}` instead of `tagAfter=tags->{tag}/{value}`, and the same for `pluginAfter`
  * `filter=metadata->plugins->{plugin}/after/0` instead of `pluginResponse={plugin}`
  * `filter=metadata->plugins->{plugin}/equals/0` instead of `noPluginResponse={plugin}`
  * `metadata->remotePlugins->{plugin}` instead of `remotePluginResponse` / `noRemotePluginResponse`

  The replication endpoints keep `modifiedAfter`.
* Removed the unused `noDescendents`, `noSources` and `noResponses` filters.
* Removed the `metadata->modified` sort, use `metadata->newResponse` instead.
* JSONB and tag value sorts (`tags->…`, `plugins->…`, `config->…`, `external->…`, `defaults->…`, `schema->…`, `metadata->newResponse` and `metadata->newReaction`) now sort missing values first ascending and last descending, instead of as `''` or `0`. Values that are not a number (`:num`), not an array (`:len`) or not a duration (`:dur`) count as missing. Metadata counts still count missing as `0`.
* Added `created` to the `_config/index` template (enabled by default) to index sorting and filtering by `created`.
* Added `title` to the `_config/index` template (enabled by default) to index sorting by `title`.
* The `tags`, `sources`, `alts`, `responses` and `internalResponses` options of the `_config/index` template now also index sorting and filtering by `tags:len`, `metadata->expandedTags`, `sources:len`, `alternateUrls:len`, `metadata->responses` and `metadata->internalResponses`, on PostgreSQL and SQLite.
* `newResponse`, `newReaction`, `hotTags`, `hotPlugins`, `hotResponses` and `hotRemoteResponses` indexes now also serve filters. Each hot plugin builds one index for its filters and both sort directions.
* Only mods can filter or sort Users by `external`, since it is hidden from other users. Non-mods get `403 Forbidden` for `external->` filters on Users, and `external->` sorts are ignored.

## Upgrading

* Clients must switch to the new filter parameters; the removed parameters are ignored.
* Existing `newResponse`, `newReaction`, `hotTags` and `hotPlugins` indexes are dropped and rebuilt on startup to match the new sort order.
