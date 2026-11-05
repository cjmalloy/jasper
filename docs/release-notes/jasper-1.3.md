# v1.3 Release Notes

This release includes changes accumulated on the v1.2 line since v1.2.0.

## Server

* Added multi-tenant support. Each origin acts as a separate tenant, with tenant-aware backups, replication and user management. Admins in the root origin can manage all tenants.
* Added push replication alongside pull replication, with optional push on change, and replication of cached files.
* Added websockets for live updates, plus Redis-backed messaging and caching for running clustered servers.
* Added server-side scripting in JavaScript, Python and Bash. Scripts can run as delta scripts in response to Ref changes or on a schedule with the new cron plugin.
* Added a web scraper and file cache, including scraping titles, published dates, thumbnails, video and Open Graph / JSON-LD metadata.
* oEmbed providers are now configured by plugins and cached on the server.
* Improved RSS feeds: ATOM feeds discovered from HTML, ETag and If-Modified-Since support to skip unchanged feeds, and an option to strip the query from entry URLs.
* Added an SMTP webhook for receiving email as Refs.
* Added SSH tunnels through the `+plugin/origin/tunnel` plugin and authorized SSH keys for users.
* Added JSON merge patch for Refs, Exts, users, plugins and templates.
* Added seal tags, the Banned role, and configurable minimum roles for reading, writing, modding and backups.
* Added preloading static files and configs at startup.
* Added new filters and sorts for Refs and tags, including published, created and metadata modified dates, scheme, tag level, tag count and untagged.
* Tags can now contain numbers and use `.` as a sub-delimiter.
* Added generated OpenAPI (Swagger) docs.

## Reference Client

* Added mods, which bundle plugins and templates together, and a modlist page.
* Added new templates: blog, chat, folder, home page and poll.
* Added new plugins: todo, playlist, file, table, oEmbed, fullscreen, picture-in-picture, cron and delta.
* Added chess and backgammon, including playing chess on kanban boards.
* Added AI chat, summaries and DALL-E image generation as mods that run as server scripts.
* Added threads with inline replies, voting, reposts and snippets.
* Added kanban badges, private kanbans and better mobile drag and drop.
* Added a tags page, breadcrumbs, a tag query editor, multi-sort and bulk tools for acting on many Refs at once.
* Added an editor toolbar, Mermaid diagrams, copy buttons on code blocks and HLS video playback.
* Expanded embeds: `![]()` can embed Refs, embeds work in comments, and oEmbeds can be shown inline.
* Added alarms, direct messages and reports tabs to the inbox.
* Added multiple themes and theme packs, including night and terminal themes and a mac theme.
* Added live updates over websockets, installable PWA support and an offline banner.
* Added drag-and-drop uploads, bookmark import and a QR scanner on the submit page.
* Plugins and templates can define forms and UI with Handlebars, and can be exported and uploaded.

## Upgrading

* Environment variables now use the `JASPER_` prefix instead of `APPLICATION_`.
* The `admin` profile was removed and the default role is now anonymous. Use a JWT or the preauth headers to authenticate users.
