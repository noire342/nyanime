# Home Panorama

Panorama is the Android Home presentation shared by video and manga. It changes
presentation only: extension manifests, routes, filters, sections, date selection,
ranking, chapter shortcuts, source alternatives and pagination remain authoritative.
The app contains no provider-specific routing or catalogue assumptions.

## Featured carousel

- A lazily composed portrait pager presents one large cover and smaller side previews.
- Swiping works in both directions across the collection boundary. Pager positions
  are virtual; content keys and selected-title recovery use extension identities.
- Refreshes retaining the same identities keep the pager and existing artwork. If
  identities change, the selected work is located in the updated collection.
- Rotation follows six idle seconds. Pointer interaction, parent scrolling, hidden
  sections, source dialogs, navigation and background lifecycle suspend rotation.
- Reduced motion and touch exploration disable automatic rotation. Manual navigation
  remains available. Accessibility reports the real collection size.
- Scale, opacity and perspective are draw-layer changes. The caption reserves two
  title lines and a metadata line; it fades with the gesture without resizing the list.
- Position is announced to accessibility without visible dots below the title.
  Source actions fade out before the centered cover changes.
- Navigation and category activity use tracked composition locals: only consumers
  of the changing state are invalidated, not every descendant of the screen.
- Images retain their previous painter during refresh/failure, with existing retry
  and source-resolution paths. Portrait transitions into detail screens are retained.
- Rounded artwork clips are applied inside the shared bounds. The overlay therefore
  retains the source silhouette while it blends into the detail hero, in both directions;
  clipping only the parent card would be lost as soon as the overlay starts.

## Personal sections

Continue watching presents one landscape card per title in a horizontal row. Artwork,
a direct resume action, elapsed/total time, progress and the episode name have separate
space. The next card peeks in on phones; tablet cards have a bounded width. All entries
remain reachable, with stable identity keys. The menu retains details, hiding with Undo
and the optional unfinished-ending action. Skeletons reserve the same card geometry.

Continue reading retains its compact columns of up to three cards. Manga cards resume
the stored chapter; progress not present in the history contract is not invented.

Updates use compact cards with medium-specific borders. Their existing dismissal,
reading/playback and navigation handlers are unchanged. New-content acknowledgement
still targets the actual local section, including when the user reaches it by scrolling.

## Validation

Unit tests cover both circular boundaries, single-title collections, restored selection
and shared placeholder geometry. Native Compose previews cover narrow and large-text
layouts, light/dark themes and loading, without using an emulator.

On a physical device, verify swiping through both boundaries, idle rotation, refresh,
return from details, source selection, resume actions and the updates shortcut. Frame
measurements describe the tested device and session, not a guarantee for all hardware.
