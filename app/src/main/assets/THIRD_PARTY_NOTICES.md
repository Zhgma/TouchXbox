# Third-party notices

The HID report descriptor and report field layout in `HidDescriptor.java` / `PadState.java` follow the Xbox 360 compatible UHID gamepad convention from scrcpy:

- https://github.com/Genymobile/scrcpy/blob/master/app/src/hid/hid_gamepad.c
- https://github.com/Genymobile/scrcpy/blob/master/app/src/uhid/gamepad_uhid.c
- Copyright (C) 2018 Genymobile
- Copyright (C) 2018-2026 Romain Vimont
- Apache License 2.0, reproduced in LICENSE.

Changes: Java implementation, explicit padding for the final HID hat nibble, touch-driven state, app-specific daemon, authenticated local socket, foreground overlay UI, session watchdog and test screen. This is an independent application, not a scrcpy or Microsoft product.

UHID ABI reference: https://github.com/torvalds/linux/blob/master/include/uapi/linux/uhid.h

Android Xbox 360 key layout reference: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/data/keyboards/Vendor_045e_Product_028e.kl

Microsoft/Xbox names and vendor/product identifiers describe the intended input compatibility. This application does not implement USB XInput transport, Xbox console authentication, or force feedback.
