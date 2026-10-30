# javadrone — Java control for the Parrot AR.Drone

A pure-Java library (no native code) for flying a Parrot AR.Drone over Wi-Fi, plus
utilities and demo apps built on top of it. It's based on the Codeminders
[javadrone](http://code.google.com/p/javadrone) project, version 1.3, under the New BSD License.

## Modules

| Module | Artifact | What it is |
|---|---|---|
| [`javadrone-api`](javadrone-api) | `javadrone-api.jar` | The core library: connection, AT command protocol, navdata and video decoding. |
| [`javadrone-utils`](javadrone-utils) | `javadrone-utils.jar` | HID game-controller support (PS3, AfterGlow, MotioninJoy), keyboard controller, image/video recording, and tools for replaying data logs. |
| [`controltower`](controltower) | `controltower-full.jar` | A Swing desktop app for flying the drone, with live video, gauges, and configurable controls. |
| [`controltower-android`](controltower-android) | `controltower-android.apk` | An Android version of ControlTower that uses USB-host game controllers. **The root build leaves it out** (commented out in `pom.xml`). |
| `lib/maven` | — | A file-based Maven repository that holds `com.twilight:h264-decoder:1.0`, which `javadrone-utils` uses. |
| `scripts` | — | `ar_drone_connect.py`: a Linux helper that finds the drone's Wi-Fi network and joins it. |

How the modules depend on each other:

```
controltower ──► javadrone-utils ──► javadrone-api
      └────────────────────────────────►┘
controltower-android ─────────────────► javadrone-api
```

## Requirements

- JDK 6 or later. The code targets Java 1.6 source/target levels; newer JDKs may need
  `-Dmaven.compiler.source/target` raised, because they no longer support 1.6.
- Maven 3.
- For `javadrone-utils` and `controltower`: `com.codeminders:hidapi:1.1`, which must be
  resolvable from your Maven repositories (it isn't vendored in `lib/`).
- For Android: the Android SDK and `android-maven-plugin` 3.3.0.

## Building

From the repository root:

```sh
mvn install
```

This builds `javadrone-api`, `javadrone-utils` and `controltower` in order. The output
goes to each module's `target/` directory.

To build the Android app, install the parent and API first, then run the build in its directory:

```sh
mvn install
cd controltower-android && mvn install
```

## Running the tests

```sh
mvn test
```

The tests use JUnit 4, which is declared once in the root `pom.xml`. They don't need a
drone or any hardware. They cover:

- **`javadrone-api`:** the command queue (priority, replacement, size limit, sticky
  repeats), AT command encoding, navdata packet parsing, controller-state decoding, and
  the log chunk format.
- **`javadrone-utils`:** the MJPEG/AVI writer.
- **`controltower`:** how button mappings are saved to and loaded from preference strings.

Run the build with JDK 8. The modules target Java 1.6, which JDK 12 and later can't compile.

## Connecting to the drone

The drone creates its own Wi-Fi network named `ardrone_<number>`. Join that network
before you connect. The drone is always at **192.168.1.1**.

On Linux you can run the helper script as root. It scans `wlan0`, joins the drone's
ad-hoc network, gets an address over DHCP, and pings the drone:

```sh
sudo python2 scripts/ar_drone_connect.py
```

The script is written for Python 2, and the interface name `wlan0` is hard-coded in its `INTF` variable.

## Running ControlTower

```sh
cd controltower
mvn exec:java          # or: java -jar target/controltower-full.jar
```

At startup, ControlTower looks for an HID game controller. If it doesn't find one, it
uses the keyboard.

**Default controls**

| Action | Gamepad | Keyboard |
|---|---|---|
| Take off | START | Enter |
| Land | SELECT | Space |
| Reset (clear emergency + flat trim) | PS | R |
| Cycle video camera | Triangle | C |
| Take snapshot | Square | F |
| Start/stop video recording | Circle | E |
| Roll / pitch (strafe, forward/back) | Left stick | W A S D |
| Yaw / altitude (turn, up/down) | Right stick | Arrow keys |

You can remap buttons in the app's control configuration dialog. ControlTower saves the
mappings in Java Preferences.

## Using the API

The smallest program that flies the drone is
[`TakeOffAndLand.java`](javadrone-api/src/main/java/com/codeminders/ardrone/examples/TakeOffAndLand.java):

```java
ARDrone drone = new ARDrone();       // defaults to 192.168.1.1
drone.connect();
drone.clearEmergencySignal();
drone.waitForReady(3000);            // block until the drone reaches DEMO state
drone.trim();                        // flat trim; drone must be on level ground

drone.takeOff();
Thread.sleep(5000);
drone.land();
Thread.sleep(2000);
drone.disconnect();
```

Add `javadrone-api` to your project as a dependency:

```xml
<dependency>
  <groupId>com.codeminders</groupId>
  <artifactId>javadrone-api</artifactId>
  <version>1.3</version>
</dependency>
```

[`docs/API.md`](docs/API.md) covers the rest: the `ARDrone` API, listeners, the
connection state machine, how commands are queued, and the internals.

## Known limitations

- Video decoding works only for the **AR.Drone 1.0**. When the API detects an AR.Drone 2.0,
  it opens the TCP video stream but has no built-in decoder. You can plug one in with
  `setExternalVideoDataDecoder(...)`. `javadrone-utils` includes an experimental
  `TestH264DataDecoder`.
- `controltower-android` is excluded from the default build.
