# v1.3 Release Notes

## Server

* Added dynamic sorting by fields in plugin, metadata, config, and external data, including numeric, length, and array-element sorting.
* Added per-origin request and script limits, with configurable limits for individual scripts.
* Added support for running asynchronous work and scripts on virtual threads, with backpressure to keep busy servers responsive.
* Added response plugins and metadata counts for Refs that respond to another Ref.
* Added external IDs for matching Jasper users to accounts in an external authentication system.
* Added support for HTTP range requests when proxying media, so playback can seek within audio and video.
* Added origin wildcards and additional controls for replication, including file selection patterns for backups and preloading.
* Added options for RSS feeds, including matching feed items by text and removing URL fragments.
* Improved validation and access control for public tags, qualified tags, and user permissions.
