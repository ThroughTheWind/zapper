# PairingFlow

The shared pairing screens every adapter drives (spec F2): connecting, awaiting approval, optional PIN, paired, denied / timed out.

- **Awaiting approval:** PairingProgress hero tells the user where to look; steps show progress. Samsung uses this.
- **PIN:** for adapters that need a code (Android TV, Philips, Hisense); numeric pad, auto-submits.
- **Paired:** `Success` haptic, suggest a friendly name (editable), then straight to the remote.
- **Denied / timed out:** `Failure` haptic, plain explanation, how to undo a past Deny, Try again.
- Token rejected later (FR-2.4): the flow reopens on step 1 with a one-line reason above the hero.
