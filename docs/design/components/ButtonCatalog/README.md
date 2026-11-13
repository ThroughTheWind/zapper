# ButtonCatalog

The bottom sheet in edit mode where users pick buttons and composite controls to add to the current page.

- Groups from the spec (FR-8.3) as filter chips: Navigation, Volume & Channel, Media, Numbers, Apps, System, Spacer/Label. Search filters across all groups.
- Each item is a 56px preview key with its default emphasis and a `label-medium` caption, 4 per row. Composite controls (D-pad, Volume rocker, Number pad) are single items.
- Buttons the connected TV doesn't support are hidden here (FR-4.5).
- Tap places the item in the first free slot on the current page; drag drops it where the finger lifts.
- Apps group lists the TV's installed apps when available, then a curated list.
