# Repository ownership and OTA continuity

The Android repository is now [owouwuiwi/nyanime](https://github.com/owouwuiwi/nyanime).
The existing repository is transferred, not copied: Git history, releases, tags,
repository identity and signing configuration remain attached to it.

## Installed apps

New builds query `owouwuiwi/nyanime` first. `noire342/nyanime` remains an explicit
fallback for unavailable endpoints. A successful primary response is authoritative;
authentication failures, rate limits and invalid responses are not silently hidden.
Cancellation stops the whole lookup. Failed requests never mean “no updates”.

GitHub redirects the old repository and release API addresses to the transferred
repository. Previously shipped apps keep their original endpoint and use this
redirect. Every future publication still includes the permanent `r<commit-count>`
compatibility release and fixed APK asset names described in [versioning](versioning.md).
Four-part version clients use the canonical numeric release and their selected channel.

**Never recreate a repository at `noire342/nyanime`.** Doing so removes GitHub's
redirect and breaks clients that still use the previous address.

The Android application ID and signing certificate are unchanged. Version codes
keep increasing. Update-check cache keys include the publisher so a migration
does not inherit a three-day delay from checks against the previous address.

## Website and links

The website and Android App Link landing page stay in their separate Pages repository
at `https://noire342.github.io/`. Shared links, signing association and installed apps'
accepted host remain unchanged. The website's download and documentation links point
to the organization repository.

GitHub Pages does not redirect automatically when its own repository is transferred.
Moving the website would require a separate compatibility migration; it is not part
of transferring the Android repository.

## Operations

The release workflow uses `GITHUB_REPOSITORY`, so publications target the current
owner automatically. Repository secrets remain attached after transfer; the workflow
still verifies the signing certificate before building and publishing.

Organization membership visibility is separate from commit attribution. Private
membership does not remove author information from preserved public history.

See [GitHub's repository transfer documentation](https://docs.github.com/en/repositories/creating-and-managing-repositories/transferring-a-repository).
