# Dialog

M3 basic dialog for confirming destructive or irreversible actions: Reset layout, Forget TV, Delete page, Discard edits.

- `surface-container-high`, `radius-xl`, 24 padding, `headline-small` title as a question, body says exactly what will be lost.
- Actions: text buttons, Cancel first, the destructive verb last in `error` ("Reset", "Forget", "Delete"). Never "OK".
- Not used for information; that's a Banner or snackbar.
