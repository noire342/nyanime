# Manga Home capabilities

Manga source APKs may expose the same declarative asset as anime sources:
`assets/aniyomi/home-v1.json`. The manga registry only inspects installed,
trusted sources returned by the manga extension loader, including private APKs.
Package ownership, source name and language must match the installed instance.
Disabled sources and languages do not expose a Home.

The Manga tab offers Home and Biblioteca when a compatible extension is available.
Without that capability it opens the existing library directly. Library search,
categories, downloads, selection actions, title details and the reader keep their
existing routes. The manga theme remains independent of ModernUI.

## Presentation data

The stable SManga interface is unchanged. Hosts may additionally implement
SMangaHomeMetadata on objects returned by SManga.create(). Its public
setHomePresentation(String) setter accepts optional versioned JSON. Extensions
compiled against an older API can discover this public setter; if it is absent,
they must still return ordinary valid SManga entries.

Example presentation:

```json
{
  "version": 1,
  "id": "chapter-event-18",
  "badges": ["Manga", "In corso"],
  "details": ["Letto: 12500 volte"],
  "rank": 1,
  "chapters": [
    {"url": "/series/example/read/18", "label": "Volume 03 · Capitolo 18", "date": "12 settembre", "isNew": true}
  ],
  "sectionTitle": "Ultimi capitoli"
}
```

The host bounds this payload to 8192 characters, six short badges/details and
five chapter actions. Unsupported versions and malformed optional metadata are
ignored. Chapter URLs must be relative source paths; protocol-relative URLs,
external URLs and control characters are rejected.

Event identity is separate from library identity: two trending chapters may share
one manga URL and local manga ID. Metadata lives only in the Home item, never in
the manga description or library fields. Source artwork is presented without
replacing library custom covers or changing chapter flags and reading progress.

## Layouts and archive links

Manga sections support chapters, updates, ranking, featured and posters layouts.
Unknown layouts fall back to posters. Section order and filter choices come from
the extension. The updates layout displays all provided chapter actions and dates;
ranking displays the supplied position and details.

An optional moreFilters map on a section opens the existing source catalogue with
those public select-filter values. It uses the same bounded label/value rules as
filters. Unsupported archive filters hide only that optional action. Categories
and search also use the source's public filters and existing catalogue screen.

## Requests, navigation and privacy

Each request creates fresh filters, runs off the main thread and is limited to
30 seconds. Two concurrent requests are allowed. Access is checked again before
results are accepted. Per-section failures are retryable and retain existing
content while refreshing. Pagination deduplicates presentation identities.

A chapter action resolves the exact local chapter URL. If missing, it fetches
and synchronizes the source chapter list, then resolves that URL again. It never
guesses by chapter number and never inserts a partial chapter list that could
delete other chapters.

Continua a leggere uses the existing local history and chapter order. In Solo
scaricati no Home feed requests run and resume selects an available download.
Incognito hides local reading history and changes to extension access discard
Home rows. There is no additional persistent manga Home cache.
