# Theming & customisation

What the user can change, and how each choice maps to tokens. Everything here lives in `AppearancePrefs` (`compose/Theme.kt`).

## Appearance settings

| Setting | Options | Default |
|---|---|---|
| Theme | System, Light, Dark, OLED black | System |
| Colour | Dynamic (Android 12+), Indigo, Teal, Amber, Rose, Green, Graphite | Dynamic, else Indigo |
| Key style | Rounded, Squircle, Circle, Square | Rounded |
| Haptics | Off, Light, Strong | Light |
| Key labels | Icons, Icons + text | Icons |

## Per-widget options (edit mode properties sheet)

- **Action:** any logical action the current TV adapter supports; unsupported actions are hidden, not greyed.
- **Emphasis colour:** Standard, Primary, Secondary, Tertiary, Ghost (Power keys: Power or Standard).
- **Icon:** a Material Symbol from a curated picker, or the app's icon for shortcuts.
- **Label:** optional caption, `key-label`, 6 characters maximum on a 1x1 key.
- **Size:** the widget's allowed spans.

Colour choices are always theme roles, never free hex values, so every layout stays legible in light, dark and OLED and when the user switches presets. (Free custom colours could come later as a "Custom seed" that generates a whole scheme, which keeps this guarantee.)

## Default layouts

Each TV adapter ships a default layout built from the same widgets; the Samsung default is shown in the *RemoteScreen* preview. Users can reset to it at any time.
