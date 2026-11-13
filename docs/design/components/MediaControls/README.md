# MediaControls

A one-row transport bar: rewind, play/pause, fast-forward, with an optional secondary row.

- Default 4x1: Standard rewind, a 2-cell Primary pill play/pause (`radius-full`), Standard fast-forward. Rewind and fast-forward repeat on hold.
- Secondary row (optional widget): Ghost previous / stop / record / next.
- Play/pause is a single toggle because most TVs don't report playback state; if the adapter does, swap the icon between `play_arrow` and `pause`.
- Spans: 4x1 or 3x1 (play/pause becomes 1 cell).
