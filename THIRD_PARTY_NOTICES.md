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


The NS Pro and DualShock 4 codecs are independent implementations of the public
report layouts. Protocol and driver references (no kernel C source is included):

- Nintendo Switch reverse engineering notes: https://github.com/dekuNukem/Nintendo_Switch_Reverse_Engineering/blob/master/bluetooth_hid_notes.md
- Linux hid-nintendo: https://github.com/torvalds/linux/blob/v6.1/drivers/hid/hid-nintendo.c
- Linux hid-sony: https://github.com/torvalds/linux/blob/v4.19/drivers/hid/hid-sony.c
- Android input-device sensor API: https://developer.android.com/reference/android/view/InputDevice#getSensorManager()
- Android standardized DS4 mapping: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/data/keyboards/Vendor_054c_Product_05c4_Version_8000.kl

FPV mode is a generic USB HID joystick/radio channel layout, not CRSF or SBUS.
The virtual HID keyboard uses the standard Keyboard/Keypad usage page bitmap.
Nintendo, PlayStation and Xbox names/identifiers indicate compatibility targets;
this project is not affiliated with their owners.

Shizuku integration includes the official API, provider, aidl and shared libraries,
version 13.1.5, under the MIT license. Source: https://github.com/RikkaApps/Shizuku-API
and Maven Central `dev.rikka.shizuku`. The complete license and copyright notice
are in `libs/Shizuku-API-LICENSE.txt`; extracted JAR checksums are in
`libs/SHA256SUMS.txt`. AndroidX annotation 1.3.0 is included under Apache-2.0,
with its license in `libs/AndroidX-LICENSE.txt`.

The use of a Shizuku UserService for privileged local input was informed by
GKME's documented local mode: https://github.com/4zyz4/GKME . No GKME source code
is included. TouchXbox uses its own Binder service and existing Java UHID codecs.
The separately installed Shizuku manager is not included in this package.
