# Changelog

## 2.0 (2026-11-06)

A complete rework: StickLab now covers sticks, triggers, buttons and lights, and all of it fits into profiles.

### Fixed

- **Deadzone changes work right away.** In 1.0 a new deadzone only took effect after restarting the Odin. Now it applies as soon as you press Apply, and it stays after a restart.
- **The screen shows what is really active.** The value comes from the Odin itself, not from the last slider position.
- **Reset goes back to your own values.** "Restore original" brings back what was set when you first opened StickLab, not a factory default.
- **Pressing the button that Android treats as Back no longer closes the app by accident.**

### Added

- **Profiles:** up to 4 named profiles with a color. Switching sets all values at once and flashes the stick LEDs in the profile color.
- **Quick Settings tile:** switch profiles without opening StickLab.
- **Auto-Set:** measures how far your sticks stay off center after you let go in 8 directions, suggests a deadzone and checks it before you apply it.
- **Finer deadzone:** steps of 0.1 % instead of 0.5 %.
- **Sensitivity and trigger deadzone** for each stick and for L2/R2, in the same ranges as the Odin menu.
- **M1 and M2:** all 22 entries of the Odin menu, shown on a picture of your Odin with the names printed on your buttons.
- **Live view:** where the sticks and triggers are, exactly as games get them.
- **Curve mode (experimental, off by default):** a virtual controller that applies your settings in every game:
  - response curves: Linear, Precision, Fast, S-Curve, Shooter, or your own with 3 to 5 points
  - outer deadzone: full output before the stick reaches the end
  - anti-deadzone: skips the deadzone built into a game
  - full L2/R2 output from part of the way
- **Restore everything:** brings back every value from before StickLab and stops Curve mode. Works by touch, also when the controller does not respond.
- **Device check:** on other devices StickLab sends nothing. Curve mode only runs on tested firmware.
- **Backgrounds:** Neon, Ocean, Ember and Graphite.

### Changed

- **New interface:** dark design with side navigation, made for landscape, no scrolling, usable by touch and D-pad.
- **In the Sticks tab the left stick only shows its position,** so you can check for drift without moving anything. D-pad and touch keep working there.
- **Ambilight reminds you** when the "Joystick light" tile in Quick Settings is off, because the LEDs stay dark without it.

### Known limitations

- Curve mode is experimental and runs on firmware 1.0.0.187. It has been tested in games through GameNative, the virtual controller also with GeForce NOW. Other games and emulators may treat it differently. On other firmware it stays off.
- Turn Curve mode on before starting a game. Switching during a game can make the game lose the controller.
- Only the AYN Odin 3 is supported. Support for the Odin 2 Portal may come later.

### Updating from 1.0

Install 2.0 over your current version. Downgrading to 1.0 needs an uninstall.

## 1.0 (2026-10-06)

First release: deadzone for both sticks from 0 to 20 %, Ambilight for the stick LEDs with fine tuning, English and German.
