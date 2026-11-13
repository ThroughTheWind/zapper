# PageManager

Controls for a layout's pages: the page dots on the remote, the page bar in edit mode, and the trash zone shown while dragging.

- **Page dots:** under the canvas, current page a 16px `primary` pill, others 6px `outline` dots. Swiping horizontally changes page (`slowSpatial` spring).
- **Page bar (edit mode):** pills per page (current = `primary`), "+ Page" outlined. Tap selects, long-press drags to reorder, tap the current page again for Rename / Delete. Deleting asks for confirmation if the page has buttons.
- **Trash zone:** replaces the top bar while dragging a widget. Idle: 2px `error` outline + label; hovered: `error-container` fill, "Release to remove", `DragSnap` haptic on enter.
