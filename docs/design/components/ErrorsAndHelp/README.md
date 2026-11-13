# ErrorsAndHelp

What Zapper shows when things go wrong (spec F3, F7, FR-2.4) and how it confirms destructive actions.

- **Offline:** status chip turns error, keys go Disabled, an error Banner offers Retry and Switch TV. Pressing a key triggers reconnect; nothing is queued (FR-3.5).
- **TV off:** keys disabled except Power. If power-on fails, a one-time info Banner explains how to enable it on the TV (FR-7.2).
- **Re-pairing:** when a stored token is rejected, pairing reopens with a short reason.
- **Confirm:** Dialog for Reset layout, Forget TV, Delete page.
