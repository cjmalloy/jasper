# v1.5 Release Notes

This release includes changes accumulated since v1.4.0. Only the latest minor release line gets fixes.

## Server

* Replaced the per-field filter parameters with `filterBefore`, `filterAfter` and `filterEquals` on the Ref, Ext, User, Plugin and Template page and count endpoints. Anything you can sort by, you can filter by: each filter is a sort column followed by `/` and a value, e.g. `filterAfter=modified/2024-01-01T00:00:00Z`, `filterBefore=tags->plugin/progress:num/50`, `filterAfter=plugins->_plugin/cache->contentLength:num/1000`, `filterAfter=metadata->remotePlugins->plugin/user/read/10` for Refs read by more than 10 users, or `filterAfter=config->pinned:len/0` for Exts. Filters use the same expression as the sort, so the same indexes apply. Filters on private plugins or tags need read access, like sorts. See the [README filtering section](../../README.md#filtering).
* Removed `modifiedBefore`, `modifiedAfter`, `publishedBefore`, `publishedAfter`, `createdBefore`, `createdAfter`, `responseBefore`, `responseAfter`, `tagBefore`, `tagAfter`, `pluginBefore`, `pluginAfter`, `pluginResponse`, `noPluginResponse`, `remotePluginResponse` and `noRemotePluginResponse` from the page and count endpoints. Use the filter parameters instead:
  * `filterAfter=modified/{date}` instead of `modifiedAfter={date}`
  * `filterAfter=metadata->newResponse/{date}` instead of `responseAfter={date}`
  * `filterAfter=tags->{tag}/{value}` instead of `tagAfter=tags->{tag}/{value}`, and the same for `pluginAfter`
  * `filterAfter=metadata->plugins->{plugin}/0` instead of `pluginResponse={plugin}`
  * `filterEquals=metadata->plugins->{plugin}/0` instead of `noPluginResponse={plugin}`
  * `metadata->remotePlugins->{plugin}` instead of `remotePluginResponse` / `noRemotePluginResponse`

  The replication endpoints keep `modifiedAfter`.
* Removed the unused `noDescendents`, `noSources` and `noResponses` filters.
* Removed the `metadata->modified` sort, use `metadata->newResponse` instead.
* JSONB and tag value sorts (`tags->…`, `plugins->…`, `config->…`, `external->…`, `defaults->…`, `schema->…`, `metadata->newResponse` and `metadata->newReaction`) now sort missing values first ascending and last descending, instead of as `''` or `0`. Values that are not a number (`:num`), not an array (`:len`) or not a duration (`:dur`) count as missing. Metadata counts still count missing as `0`.
* Added `created` to the `_config/index` template (enabled by default) to index sorting and filtering by `created`.
* `newResponse`, `newReaction`, `hotTags`, `hotPlugins`, `hotResponses` and `hotRemoteResponses` indexes now also serve filters. Each hot plugin builds one index for its filters and both sort directions.
* Only mods can filter or sort Users by `external`, since it is hidden from other users. Non-mods get `403 Forbidden` for `external->` filters on Users, and `external->` sorts are ignored.

## Upgrading

* Clients must switch to the new filter parameters; the removed parameters are ignored.
* Existing hot tag indexes are dropped by a database migration, since the tag value functions changed. `newResponse`, `newReaction`, `hotTags` and `hotPlugins` indexes are rebuilt on startup when the `_config/index` template is enabled.
