# App scope

## Current

Floating native-receiver controls, source selection/restore, adapter control, Bluetooth settings access and playback status. The bar retains its position and uses a foreground notification.

## Further work

- Diagnose the stock Bluetooth toggle fault; assess system changes only if necessary.
- Establish an app-accessible property/restart helper if needed for native-mode setup.
- Investigate simultaneous receive/transmit profiles and headphone-volume control.
- Consider collapse/expand behavior or automatic source selection if required.

The current app does not write system properties, restart Bluetooth, switch audio roles or launch at boot.
