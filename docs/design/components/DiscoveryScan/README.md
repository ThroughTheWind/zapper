# DiscoveryScan

The scanning indicator and the not-found fallback shown on first launch and when adding a TV.

- **Scanning:** three `primary` rings pulse out from a `primary-container` disc holding a `tv` icon (2.4s loop, static under reduced motion). Found TVs appear below as DeviceCards as they arrive; the scan keeps running until timeout.
- **Timeout (~10s) with nothing found:** a neutral Banner (not an error: nothing is broken yet) with the troubleshooting tips from the spec, a "Scan again" text action, and a tonal "Add by IP address" button.
- Copy says "TV", never protocol names (SSDP, mDNS).
