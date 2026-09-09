# Pinned artifact mirror

JitPack returned HTTP 500 / missing artifacts for FlexibleAdapter on September 9, 2026,
blocking the stability release. This is the unmodified AAR and POM recovered from the
local Gradle cache of the project's existing dependency, not a version downgrade.

Coordinates: `com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533`.
Source: https://github.com/arkon/FlexibleAdapter/tree/c8013533
License: Apache-2.0, included as `FlexibleAdapter-LICENSE`.

SHA-256:

- AAR: `41929c785c249e0395faf89fd6bb253aafd65d44d88dbeaa46ecd9658d706cc4`
- POM: `7ee6f68cfc5a369efbd1dea8c4ee2b7b93979a4abe16d2f289e21acecaa44fdf`

Repository content filtering limits this mirror to that exact version. New upstream
versions resolve normally. The source files and native libraries in the AAR are unchanged.
