# javadrone API guide

This guide covers the `javadrone-api` library and how its internals fit together. For
build and run instructions, see the [README](../README.md).

All classes are under `com.codeminders.ardrone`.

---

## 1. Network protocol at a glance

The drone listens at `192.168.1.1` (`ARDrone.DEFAULT_DRONE_IP`). The library uses these ports:

| Port | Transport | Direction | Purpose | Handled by |
|---|---|---|---|---|
| 5556 | UDP | client → drone | AT commands (`AT*REF`, `AT*PCMD`, `AT*CONFIG`, …) | `CommandSender` |
| 5554 | UDP | drone → client | Navigation data (navdata) | `LigthUDPDataReader` + `ARDrone10NavDataDecoder` |
| 5555 | UDP (1.0) / TCP (2.0) | drone → client | Video stream | `LigthUDPDataReader`/`TCPDataRader` + video decoder |
| 5559 | TCP | drone → client | Configuration dump (`readDroneConfiguration()`) | `ARDrone` |
| 21 | FTP | drone → client | `version.txt`, used to detect the drone version | `DroneFTPversionReader` |

The AR.Drone SDK Developer Guide documents the AT command and navdata formats. The
Javadoc comments in this code refer to section numbers in that guide.

---

## 2. The `ARDrone` class

`ARDrone` is the main entry point. Each instance controls one drone.

### Construction

```java
new ARDrone();                                   // 192.168.1.1, 1 s reconnect timeouts
new ARDrone(InetAddress addr, int navDataReconnectTimeoutMs, int videoReconnectTimeoutMs);
```

The constructor doesn't touch the network. The work starts when you call `connect()`.

### Lifecycle

| Method | Description |
|---|---|
| `connect()` | Reads the drone version over FTP, starts the command-sender thread, enables video and automatic bitrate, starts the navdata and video reader/decoder threads, and moves to `CONNECTING`. |
| `connect(DataLogger video, DataLogger navdata)` | Same as `connect()`, but also copies the raw channel data to the given loggers. Pass `null` for either one to skip it. |
| `waitForReady(long ms)` | Blocks until the state is `DEMO`. Throws `IOException` on `ERROR` or `DISCONNECTED`. On timeout it disconnects first, then throws. |
| `disconnect()` | Queues `QuitCommand`, stops the channel processors, closes the socket, and moves to `DISCONNECTED`. |
| `getState()` | Returns the current `ARDrone.State`. |
| `getDroneVersion()` | Returns the firmware version string (e.g. `"1.10.10"`), or `null` if it can't be read. |
| `readDroneConfiguration()` | Returns the drone's full configuration dump as text. Call it only while connected, because it blocks while it waits on port 5559. |

### Flight commands

| Method | AT command | Notes |
|---|---|---|
| `trim()` | `FTRIM` | Flat trim. Call it while the drone is landed on level ground. |
| `takeOff()` | `REF` (sticky) | Ignored in emergency mode. Moves to `TAKING_OFF` until navdata reports that the drone is flying. |
| `land()` | `REF` (sticky, high priority) | Moves to `LANDING` until navdata reports that the drone has landed. |
| `hover()` | `PCMD` (hover flag) | Holds position. |
| `move(lr, fb, vz, yaw)` | `PCMD` | Each argument ranges from −1.0 to 1.0, as a fraction of the configured maximum. See below. |
| `sendEmergencySignal()` | `REF` | Cuts the motors immediately. Sent only if the drone isn't already in emergency mode. |
| `clearEmergencySignal()` | `REF` | Clears emergency mode. Sent only if the drone is in emergency mode. |
| `setCombinedYawMode(boolean)` | — | When enabled, `move()` sets the combined-yaw flag in `PCMD`. Enabled by default. |

`move()` arguments:

- `left_right_tilt` (roll, φ): negative tilts left, positive tilts right.
- `front_back_tilt` (pitch, θ): negative lowers the nose and flies forward; positive raises it and flies backward.
- `vertical_speed` (gaz): positive climbs, negative descends.
- `angular_speed` (yaw): positive spins right, negative spins left.

### Video and configuration

| Method | Description |
|---|---|
| `enableVideo()` / `disableVideo()` | Sets `general:video_enable`. |
| `enableAutomaticVideoBitrate()` / `disableAutomaticVideoBitrate()` | Sets `video:bitrate_control_mode`. |
| `selectVideoChannel(VideoChannel)` | Chooses one of `HORIZONTAL_ONLY`, `VERTICAL_ONLY`, `VERTICAL_IN_HORIZONTAL` (picture-in-picture), or `HORIZONTAL_IN_VERTICAL`. |
| `sendDemoNavigationData()` / `sendAllNavigationData()` | Switches between the reduced (demo) and full navdata sets. |
| `setConfigOption(ConfigOption, String)` | Sets a typed configuration key, e.g. `ConfigOption.ALTITUDE_MAX`, `OUTDOOR`, `EULER_ANGLE_MAX`. |
| `setConfigOption(String key, String value)` | Sets any raw `section:key` configuration value. |
| `playAnimation(Animation, int duration)` | Plays a flight animation, e.g. `WAVE`, `YAW_DANCE`, or `TURNAROUND`. |
| `playLED(LED, float freq, int duration)` | Plays an LED pattern, e.g. `BLINK_RED`, `FIRE`, or `SNAKE_GREEN_RED`. |
| `pauseNavData()` / `resumeNavData()` / `pauseVideo()` / `resumeVideo()` | Pauses or resumes a decoding thread without disconnecting. |
| `setExternalVideoDataDecoder(VideoDataDecoder)` | Replaces the built-in video decoder. Call it before `connect()`. |
| `setExternalVideoDataDecoder(NavDataDecoder)` | Replaces the built-in navdata decoder. Despite the name, this overload takes a `NavDataDecoder`. Call it before `connect()`. |

---

## 3. Listeners

Register listeners before `connect()` so they don't miss early events. The library calls
them on its decoder threads. If you update a Swing or Android UI from a listener, hand the
work to the UI thread.

```java
drone.addStatusChangeListener(new DroneStatusChangeListener() {
    public void ready() { /* drone entered DEMO: configure it here */ }
});

drone.addNavDataListener(new NavDataListener() {
    public void navDataReceived(NavData nd) {
        System.out.printf("alt=%.2fm batt=%d%% pitch=%.1f roll=%.1f yaw=%.1f%n",
            nd.getAltitude(), nd.getBattery(), nd.getPitch(), nd.getRoll(), nd.getYaw());
    }
});

drone.addImageListener(new DroneVideoListener() {
    public void frameReceived(int startX, int startY, int w, int h,
                              int[] rgbArray, int offset, int scansize) {
        // the parameters match BufferedImage.setRGB(...)
    }
});
```

Each listener type also has `remove…Listener` and `clear…Listeners` methods.

- `ready()` runs every time the drone enters `DEMO` mode, which includes after each takeoff or landing completes.
- `navDataReceived` runs only while the state is `DEMO`.
- `javadrone-utils` provides `BufferedImageVideoListener`, which assembles each frame into a
  `BufferedImage` and passes it to `imageReceived(BufferedImage)`.

### `NavData`

`NavData` is abstract. `ARDrone10NavData` is the implementation. Its main fields:

- **Attitude and motion:** `getPitch()`, `getRoll()`, `getYaw()`, `getAltitude()`, `getVx()`, `getVz()`, `getLongitude()`
- **State:** `getMode()` (`BOOTSTRAP`/`DEMO`), `getControlState()` (`CtrlState`), `getFlyingState()` (`FLYING`, `TAKING_OFF`, `LANDING`, `LANDED`), `isFlying()`, `isEmergency()`
- **Power:** `getBattery()` (percent), `isBatteryTooLow()`, `isNotEnoughPower()`
- **Fault flags:** `isMotorsDown()`, `isGyrometersDown()`, `isUltrasonicSensorDeaf()`, `isTooMuchWind()`, `isAngelsOutOufRange()`, `isCutoutSystemDetected()`, `isCommunicationProblemOccurred()`, and others
- **Vision:** `getVisionTags()`, which returns `VisionTag`s with their type, position, size, and distance in cm

---

## 4. Connection state machine

`ARDrone.State` is one of `DISCONNECTED`, `CONNECTING`, `BOOTSTRAP`, `DEMO`, `ERROR`, `TAKING_OFF`, or `LANDING`.

```
            connect()
DISCONNECTED ───────► CONNECTING
                          │ navdata reports mode = BOOTSTRAP
                          ▼
                      BOOTSTRAP ── the library sends general:navdata_demo=TRUE automatically
                          │ navdata reports mode = DEMO
                          ▼
     ┌──────────────►   DEMO   ◄──────────────┐   (ready() fires on each entry)
     │ flyingState=FLYING │ │ land()    flyingState=LANDED
     │       takeOff()    │ └──────► LANDING ─┘
     └──── TAKING_OFF ◄───┘

Any I/O failure ──► ERROR (resources released)       disconnect() ──► DISCONNECTED
```

`ARDrone.navDataReceived` also handles these protocol details on each navdata packet:

- If the drone reports *control received*, it sends the ACK (`AT*CTRL=5,0`).
- If the communication watchdog fires, it sends `AT*COMWDG` (`KeepAliveCommand`).
- It updates the emergency flag that `takeOff()`, `sendEmergencySignal()` and `clearEmergencySignal()` check.
- If the battery is low or power is insufficient, it logs a warning.

---

## 5. Command queue

Every public command method creates a `DroneCommand` and adds it to a `CommandQueue`
(capacity 64). A dedicated thread named **"Command Sender"** (`CommandSender`) takes
commands off the queue, numbers them with an increasing sequence counter, and sends each
one as a single UDP packet to port 5556.

How the queue orders and repeats commands:

- **Priority:** Higher priority goes first. The levels are `MIN_PRIORITY` (0, most AT
  commands), `HIGH_PRIORITY` (50, `LandCommand`), `VERY_HIGH_PRIORITY` (90,
  `KeepAliveCommand`), and `MAX_PRIORITY` (100, `QuitCommand`). Commands with equal
  priority go out in FIFO order.
- **Replacement:** If an equal command is already queued, the new one replaces it instead
  of adding a duplicate. Commands are equal if they produce the same AT string, or if
  they share a non-null category. Take-off and land share the `takeoffland` category,
  so a newer `land()` replaces a pending `takeOff()`.
- **Sticky commands:** `TakeOffCommand` and `LandCommand` stay in the queue after they're
  sent and repeat every 100 ms. The repeats stop when the state machine sees the
  transition finish and clears the queue.
- **Quit:** When the sender takes `QuitCommand`, its thread stops. An `IOException` while
  sending puts the drone in `ERROR`.

To add a new AT command, subclass `ATCommand` and implement `getID()` (e.g. `"CONFIG"`)
and `getParameters()`. Integers and strings are encoded as-is (strings are quoted). Floats are
sent as their IEEE-754 bit pattern, as the protocol requires.

---

## 6. Data channels (navdata and video)

```
ARDroneDataReader ──► DataDecoder (Thread) ──► ARDrone.navDataReceived / videoFrameReceived ──► listeners
        ▲                                      (wired together by ChannelProcessor)
        └── optional ARDroneDataReaderAndLogWrapper ──► DataLogger
```

- **Readers** implement `ARDroneDataReader` (`data.reader`):
  - `LigthUDPDataReader`: the default for navdata and 1.0 video.
  - `UDPDataReader`: NIO-based; used when a logger is attached.
  - `TCPDataRader`: AR.Drone 2.0 video.

  If no data arrives within the reconnect timeout, a reader reconnects.
- **Decoders** extend `NavDataDecoder` or `VideoDataDecoder`. These are threads that pull
  from the reader and call `notifyDroneWithDecodedNavdata(...)` or
  `notifyDroneWithDecodedFrame(...)`. The built-in decoders are in
  `data.decoder.ardrone10`. `BufferedVideoImage` implements the AR.Drone 1.0 P264/UVLC
  video codec in pure Java.
- **Version selection:** `connect()` reads the major version from `version.txt` over FTP.
  For version 1, it uses UDP video with the 1.0 decoder. For version 2, it uses TCP video
  with **no decoder**, unless you supplied one with `setExternalVideoDataDecoder`. If the
  version can't be read, it assumes version 1.
- **ChannelProcessor:** Connects one reader to one decoder, and provides `pause()`,
  `resume()` and `finish()`.

### Logging and replay

Pass `DataLogger`s to `connect(videoLogger, navdataLogger)` to record the raw channel data.
`javadrone-utils` has the file-based tools:

- `AsyncFileChannelDataLogger`: writes timestamped `ChannelDataChunk`s to a file.
- `FileDataReader` / `FileDataReaderAndDecoder`: read a recorded file back and run it through a decoder.
- `logs.video.VideoPlayer`: a small Swing program for watching recorded video logs.

---

## 7. Game controllers

These are the classes for handling controller input:

- `controllers.Controller` (in the api module): the abstract base class. `read()` returns a
  `GameControllerState`, which holds the buttons, two sticks in the range −128…127, and the hat switch.
- `ControllerStateChange`: compares two states, so you can react to button presses as they happen.
- `controllers.decoders.*`: turn raw HID reports from the Sony PS3, AfterGlow and
  MotioninJoy virtual pads into a `GameControllerState`.
- `javadrone-utils`:
  - `hid.HIDAPI*Controller`: HID implementations built on `com.codeminders:hidapi`.
  - `HIDControllerFinder.findController()`: returns the first supported controller it finds.
  - `KeyboardController`: maps keys to a virtual pad.
- `controltower-android`: `UsbHostController` and its subclasses do the same over the Android USB Host API.

---

## 8. Recording (`javadrone-utils`, `util` package)

| Class | Purpose |
|---|---|
| `FileImageRecorder` | Calling `activate()` saves the next frame as a numbered image file. |
| `FileVideoRecorder` | `startRecording()`, `pauseRecording()` and `finishRecording()` control the recording, which writes an MJPEG AVI file. |
| `MJPEGGenerator` | A standalone MJPEG AVI writer. |
| `RecordingSuccessCallback` | Reports `recordingSuccess(filename)` or `recordingError(filename, err, ex)`. |

Both recorders extend `ImageVideoRecorderBase`, which is a `BufferedImageVideoListener`. To record, register one as an image listener on the drone.

---

## 9. ControlTower architecture (desktop app)

`com.codeminders.controltower`:

- **`ControlTower`** is the main `JFrame`. Its update loop works as follows:
  1. Finds an HID controller, or falls back to the keyboard.
  2. Connects to the drone. On `ready()`, it selects the horizontal camera, enables combined yaw, and runs a flat trim.
  3. Polls the controller in a loop. When a button is pressed, it calls `ControlMap.sendCommand(drone, button)`.
  4. While the drone is flying, it converts stick positions to `drone.move(...)` calls. Stick
     deflection below the control threshold is ignored. The *flip sticks* option swaps
     the left and right sticks.
- **`config.ControlMap` / `config.AssignableControl`** map buttons to commands, which can
  be take-off, land, reset, cycle camera, snapshot, record video, an animation, an LED
  pattern, or a sound. Each command can have a delay. The mappings persist in Java Preferences.
- **`VideoPanel`**: the live video display.
- **`BottomGaugePanel`**: attitude, altitude and battery gauges, drawn with the SteelSeries library.
- **`DroneConfig`**: a dialog for limits such as maximum tilt, vertical speed, yaw speed and altitude.
- **`ControlConfig`** / **`KeyboardControlConfig`**: dialogs for remapping controls.

`controltower-android` follows the same design: `MainActivity`, `ControllerThread`,
`ControlMap` and `AssignableControl`, with settings managed through `SettingsPrefs`.
