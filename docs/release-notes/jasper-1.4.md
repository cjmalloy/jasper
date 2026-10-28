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

## Upgrading

* Before starting v1.4.0 with PostgreSQL, clear the stored Liquibase checksum for the changed schema changeset: `UPDATE DATABASECHANGELOG SET MD5SUM = NULL;`. Liquibase will recalculate the checksum on startup and re-run the SQL function changeset, which adds a small `instr()` function and the `tag_value`, `tag_value_num` and `tag_value_dur` functions used for tag value sorting.
* If you rely on deleted items being included in backups, set `tombstones: true` in your backup options.
* The SCIM integration no longer depends on a third-party SDK. No configuration changes are needed.
* Cloud storage needs credentials: GCS uses Application Default Credentials (e.g. GKE Workload Identity), and S3 uses the default AWS credential chain (e.g. EKS IRSA). Set `APPLICATION_STORAGE_S3_REGION` / `APPLICATION_STORAGE_S3_ENDPOINT` as needed, and `APPLICATION_STORAGE_TMP_DIR` for zip staging space.
