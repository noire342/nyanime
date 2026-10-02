# News extensions (API v1)

News is a third, optional extension family. The host understands articles and typed
capabilities, never publisher domains, selectors, brand names or site-specific fixtures.
All extraction belongs in separately built APK projects. Existing anime and manga APIs
and their extension manifests remain unchanged.

## Package contract

Build against `news-api` with **compileOnly**. Do not bundle SDK or Kotlin runtime
classes into the extension. An APK declares the feature `nyanime.newsextension` and
these application metadata entries:

```xml
<uses-feature android:name="nyanime.newsextension" android:required="false" />
<application android:label="Example News">
    <meta-data android:name="nyanime.news.api" android:value="1" />
    <meta-data android:name="nyanime.news.factory" android:value="example.news.Factory" />
</application>
```

`NewsSourceFactory.create(NewsHttpClient)` returns one `NewsSource`. Networking comes
from the host; the source owns URLs, headers, parsing and normalization. The host
enforces cancellation, bounded responses, at most three concurrent operations and
one operation per source. The existing optional `nyanime/extension-v1.json`
distribution declaration applies. Local/manual distributions are not replaced with
unrelated repository builds.

Inspection does not execute extension code. A signer-bound, local confirmation is
required before loading a source. An incompatible API, changed distribution or
untrusted signer prevents loading. Android verifies installation signatures; the
generic APK validator also understands the News family.

## Articles and capabilities

`feed(NewsRequest)` returns `NewsPage(articles, nextCursor)`. Cursors are opaque to the
host. Do not advertise a next page when the publisher only provides a finite RSS feed.
`article(NewsArticle)` retains its ID and canonical URL. Search, media filters and
source categories are optional capabilities. Unsupported search falls back explicitly
to the local cache, not to another publisher or a title catalogue.

An article has a stable publisher-owned ID, canonical original URL, headline,
publisher/language, optional author and image, publication/modification timestamps,
topics and content blocks. Dates are UTC epoch milliseconds. Unknown publication
dates are `null`; collection time is stored separately and never displayed as a
publication date. Identity uses the source and stable ID, or the same canonical URL;
similar headlines do not establish duplicates.

Content consists of sanitized Markdown text, headings, quotes, images and explicit
media links. There is no article WebView and no JavaScript execution. An incomplete
article is marked `fullText=false` and links clearly to the publisher's website.
Preserve attribution, author, image credits and licensing. Do not bypass access
controls or convert unavailable paid content into a full article.

## Personalization and notifications

The personal set is the union of the library and explicit release follows, minus
individual news exclusions. Direct catalogue IDs and verified prequel/sequel/source/
adaptation edges can establish a match. A title resemblance alone cannot. A source
topic without verified IDs can be linked explicitly to a work by the user; that
mapping is scoped to the source's stable topic ID. No tracker is modified.

Alerts default to Off. Each enabled source offers Off, Only my titles or All articles.
The first successful fetch and the first fetch after enabling alerts establish a
baseline. A restore establishes another baseline. Historical articles, undated items
and already delivered IDs never create a notification burst. A persistent pending
queue and receipts survive process restarts; retrying replaces the same notification
ID instead of duplicating it. Multiple articles from one publisher form one inbox.

Cached content appears immediately. Foreground refresh has a 15-minute freshness
window; manual refresh is available. When any alerts are enabled, WorkManager checks
hourly with a network constraint. Android may defer it: this is polling, not real-time
push. Source errors, backoff and Retry-After are isolated. No permanent service runs.

## Storage and backup

`news-v1.json` is an atomically replaced, separate local store. Ordinary summaries are
bounded to 1,000 items and unsaved full-text bodies to 40 / 4 million characters.
Explicitly saved text is never evicted automatically. Images use the existing bounded
image cache; missing offline images have a visible fallback. Video is never downloaded.

The optional protobuf field 509 in `.nyabk` carries saved/read article state,
preferences, exclusions and mappings. Older `.nyabk` and `.tachibk` remain readable.
News is restored with App settings, independently of the Library restore option.
Image caches are excluded. Trust decisions are not transferred to a different
installation; restored extensions must be locally trusted. Incognito does not record
reading progress, and downloaded-only mode does not request article data over the network.

## UI and verification

Enabling a compatible source adds News to the reorderable Home categories, without
changing the configured starting page. Latest, For you and Saved share compact cards.
Incoming articles wait behind a “Show new articles” action while the visible list is
preserved. Search from News uses the existing navigation-bar transition in article
scope. Episode/chapter release notices remain separate.

App fixtures and tests use only fictitious publishers and generic identities. Parser
and live publisher tests belong exclusively in separate extension projects. Device
checks must cover first trust, enablement, native reading, save/offline, search/back,
notification permission, restoration and large text; compilation alone is not proof
of those Android paths.
