# EditMode

The layout editor where users rearrange, resize, recolour, add and remove widgets on the remote canvas.

**Enter:** only through the explicit Edit (pencil) button in the top bar (spec FR-8.2). Long-press never enters edit mode, so a held key can't trigger an accidental edit. **Exit:** Done (saves) or system Back (asks to discard when there are changes).

## On entering

- Grid dots appear (`outline-variant`, one per cell) so the 4-column grid is visible.
- Every widget gets a 1.5px dashed `outline` and a slow ±1.5° wiggle (off with reduced motion), and a remove badge (`error-container`, top-left).
- Keys stop sending commands.
- The top bar becomes Undo · "Edit layout" · Done.

## Gestures

- **Drag** a widget: it lifts (scale 1.04, `elevation-5`, solid 2px `primary` outline, `DragStart` haptic) and snaps cell by cell (`DragSnap` haptic per new cell). The target area shows `primary-container` at 70% with a dashed `primary` border if free, or `error-container` + `error` if it overlaps. Other widgets don't reflow; overlaps are refused, so the layout never shifts under the user. Drop: `DragDrop` haptic.
- **Resize:** the round handle (bottom-right of the selected widget) snaps between the widget's allowed spans only (see `WidgetType.allowedSpans` in `compose/components/RemoteLayout.kt`).
- **Tap** a widget: selects it and opens the properties sheet: action, label, icon, emphasis colour.
- **Remove:** the badge, or drag onto the trash zone that replaces the top bar while dragging (turns `error-container` when hovered).
- **Add:** bottom sheet palette (D-pad, Touchpad, Rockers, Numbers, Media, App, Single key, Spacer). Drag from the palette onto the grid, or tap to place in the first free slot.

## Layouts

- Layouts are stored per TV and hold several pages (default Main, Apps, Numbers) swiped horizontally, with a page indicator under the canvas. In edit mode the page indicator gains add / rename / reorder / delete.
- Undo reverts the last edit. "Reset to default" in the overflow menu restores the TV adapter's default layout. Export / import as JSON via the share sheet.
- Layouts are stored as `RemoteLayout` (grid columns + placed widgets with action ids, not TV-specific codes), so a layout can move between TV brands.

## Accessibility

Every drag operation has a non-drag equivalent: the properties sheet has "Move" (arrow buttons) and "Size" (span picker), and TalkBack exposes custom actions "Move left/right/up/down", "Remove".
