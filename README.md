# Virtual Xbox Mapper (Android)

This project was rebuilt into a new Android app focused on **generic controller to Xbox-style mapping**.

## Current scope

This version is a **non-root compatibility mapper** for Android.

It provides:
- USB / generic game controller detection
- Live button and joystick input capture inside the app
- Xbox-style layout preview
- Mapping profiles saved with Room
- Foreground service toggle for compatibility mode
- Live trace panel showing what the app is doing
- Remove-all-registered mappings option

## Important limitation

On stock Android, a normal app usually **cannot create a true system-wide virtual Xbox controller** for all games.
That normally needs **root + uinput** or lower-level system integration.

So this build is designed as:
- a real controller mapper UI
- a live input monitor
- a profile manager
- a service-based compatibility scaffold

## Main features

- Detect USB / generic controller devices
- Read `KeyEvent` and `MotionEvent` gamepad input
- Map source buttons/axes to Xbox targets like:
  - A / B / X / Y
  - LB / RB / LT / RT
  - LS / RS
  - BACK / START
  - D-Pad directions
- Create multiple mapping profiles
- Clear or remove saved mappings
- See live pressed buttons on a visual Xbox layout

## Example use

1. Connect your generic controller
2. Open the app
3. Press a controller button
4. App shows the detected source input
5. Tap the Xbox target button you want
6. Save more mappings
7. Turn service ON for compatibility mode

## Future root version

If you later want a true game-facing virtual Xbox output layer, the next step would be:
- root device support
- `/dev/uinput`
- native virtual HID output

## Notes

- This app fully replaces the previous browser/AI concept.
- Do not expect stock Android non-root mode to spoof a perfect Xbox HID device in every game.
