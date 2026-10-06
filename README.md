# StickLab

Stick settings for the AYN Odin 3. No root needed.

## Features

- **Deadzone:** set the deadzone of the left and right stick from 0 to 20 % in steps of 0.5 %.
- **Ambilight:** the stick LEDs follow the colors on screen. The left stick uses the top-left area of the picture, the right stick the bottom-right area.
- **Fine tuning:** sliders for brightness, color intensity, white level, speed, green and blue level, plus a Defaults button.
- **Languages:** English and German.

## Install

1. Download the latest APK from the Releases page.
2. Open the file on your Odin 3 and allow the installation.
3. Start StickLab.

## How it works

- Settings are applied through `PServerBinder`, a system service that is built into the AYN firmware. That is why no root is needed.
- For Ambilight, Android gives the app a tiny live copy of the screen (96×54 pixels). The app reads it up to 60 times per second and updates the LEDs up to 40 times per second.
- Nothing is saved or sent anywhere. The app has no internet permission.

## Good to know

- Works only on the AYN Odin 3.
- Apps that protect their screen content (for example the YouTube app or Netflix) are seen as black, so the LEDs stay dark. YouTube in the browser works.
- The switch "Don't ask again" skips the Android screen capture prompt. It is off by default.
- Use at your own risk. This project is not affiliated with AYN.

## Credits

The call format for `PServerBinder` is documented by ClusterTune (AurelioB), which credits O2P Tweaks (FeralAI).

## License

MIT
