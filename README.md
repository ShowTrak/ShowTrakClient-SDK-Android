<div align="center">

<a href="https://showtrak.co.uk"><img src="https://showtrak.co.uk/images/logo.png" alt="ShowTrak Android SDK logo" width="128" height="128"></a>

# ShowTrak Android SDK

**Monitor and control your own Android app from ShowTrak Server.**

[Website](https://showtrak.co.uk) · [Maven Central](https://central.sonatype.com/artifact/io.github.showtrak/showtrak-sdk) · [Demo app](https://github.com/ShowTrak/ShowTrak-SDK-Android-Demo) · [Discord](https://discord.gg/DACmwsbSGW)

[![Android 7.0+][badge-android7]](https://showtrak.co.uk) [![Kotlin][badge-kotlin]](https://showtrak.co.uk)

</div>

Add a few lines to an Android app (a kiosk, a game, an interactive) and it shows up in
[ShowTrak Server](https://github.com/ShowTrak/ShowTrakServer) beside your computers: adopt it with one
click, watch its status, and trigger its actions from the desk, QLab or a Stream Deck.

## Key features

- **Shows up like any other client**: discovered and adopted in ShowTrak Server, with online, offline and degraded status.
- **Your own actions**: register actions with a name, icon and colour, and run them from ShowTrak Server, OSC or HTTP.
- **Progress feedback**: report progress and success or failure back to the operator.
- **Health reporting**: tell the server when your app is degraded and why.

## Module

- `showtrak-sdk/` - Android library module (`io.showtrak.sdk`).

## API Overview

Primary entry point: `io.showtrak.sdk.ShowTrak`

Connection lifecycle:
- `connect(context, ip, port, id?)`
- `disconnect()`
- `onStateChanged { ... }`
- `removeStateListener()`
- `release()`
- `state: ConnectionState`

Status and metadata getters:
- `getStatus(): SDKStatus`
- `getClientId(): String?`
- `isConnected(): Boolean`
- `isAdopted(): Boolean`
- `getServerHost(): String?`
- `getServerPort(): Int?`
- `getRegisteredEventIds(): List<String>`

Event registration:
- `registerEvent(name, options, handler)`
- `unregisterEvent(name)`
- `unregisterAllEvents()`

Health reporting:
- `setState("ONLINE")`
- `setState("DEGRADED", "message")`

Supported connection states:
- `DISCONNECTED`
- `CONNECTING`
- `PENDING_ADOPTION`
- `ONLINE`

## API Details

### connect

```kotlin
ShowTrak.connect(
	context = applicationContext,
	ip = "10.0.2.2",
	port = 3000,
	id = null
)
```

Behavior:
- If `id` is null or blank, SDK generates and persists a UUID-style client ID.
- If `id` is provided, it is used as-is.
- SDK handles adoption flow automatically.
- On reconnect, SDK restores adopted state for the active client ID.

### disconnect

```kotlin
ShowTrak.disconnect()
```

Behavior:
- Stops heartbeats and adoption loop.
- Closes socket connection.
- Updates state to `DISCONNECTED`.

### registerEvent

```kotlin
ShowTrak.registerEvent(
	name = "SetBoxRed",
	options = EventOptions(
		label = "Set Box Red",
		colour = 0,
		hasFeedback = true,
		icon = "circle-fill"
	)
) { ack ->
	// Perform action
	ack.success()
}
```

Rules:
- Event name must match `^[A-Za-z0-9_.-]+$`.
- Registering the same name again replaces the previous handler.
- Events are published to server after adoption and whenever set changes.
- `icon` is optional; omit it and the event shows the default `terminal` glyph.
- A handler that has not called `ack.success()` / `ack.error()` within
  `timeoutMs` is resolved as `RESOLVED_TIMEOUT`. Do slow work on your own
  thread and report progress with `ack.feedback()` — see [Ack](#ack).

### unregisterEvent

```kotlin
ShowTrak.unregisterEvent("SetBoxRed")
```

### release

```kotlin
override fun onDestroy() {
	ShowTrak.release()
	super.onDestroy()
}
```

`ShowTrak` is a process-wide singleton, so anything it holds that captures an
Activity or Fragment — a state listener, an event handler — keeps that component
alive after it is destroyed. `release()` performs a full teardown: it
disconnects, drops the state listener and every event handler, and stops the
SDK's background thread. A later `connect()` builds a fresh client, so this is
safe to recover from.

Use the narrower `removeStateListener()` and `unregisterAllEvents()` instead if
you want to keep the connection alive across the component's lifetime. Note that
`disconnect()` deliberately does **not** drop listeners, so a consumer that
reconnects keeps observing.

### setState

```kotlin
ShowTrak.setState("DEGRADED", "Projector not reachable")
ShowTrak.setState("ONLINE")
```

Rules:
- Accepted values: `ONLINE`, `DEGRADED`.
- Any other value throws `IllegalArgumentException`.
- `ONLINE` must not include a message.
- `DEGRADED` requires a non-empty message (max 256 chars).
- Last requested state is re-applied automatically after reconnect.

### onStateChanged and state

```kotlin
ShowTrak.onStateChanged { state ->
	// May run on any thread
}

val current = ShowTrak.state

ShowTrak.removeStateListener()
```

Notes:
- The listener is invoked immediately with the current state, on the calling
  thread. Later calls arrive on whichever thread changed the state, so switch to
  the UI thread before touching UI.
- Only one listener is held; registering a second replaces the first.
- A listener that captures an Activity or Fragment must be removed when that
  component is destroyed — see [release](#release).

### getStatus and metadata

```kotlin
val status = ShowTrak.getStatus()

val state = status.connectionState
val connected = status.connected
val adopted = status.adopted
val clientId = status.clientId
val endpoint = "${status.serverHost}:${status.serverPort}"
val events = status.registeredEventIds
```

Quick getters:

```kotlin
val clientId = ShowTrak.getClientId()
val connected = ShowTrak.isConnected()
val adopted = ShowTrak.isAdopted()
val host = ShowTrak.getServerHost()
val port = ShowTrak.getServerPort()
val events = ShowTrak.getRegisteredEventIds()
```

## Data Types

### EventOptions

```kotlin
EventOptions(
	label = "Readable Label",  // default: event name
	colour = 7,                // 0..7, default 7
	hasFeedback = true,        // default true
	icon = "circle-fill",      // default "terminal"
	timeoutMs = 15_000         // default 15_000
)
```

Validation:
- `colour` must be in range `0..7`.
- If `label` is provided, it must be non-blank and `<= 80` chars.
- `timeoutMs` must be between `1_000` and `600_000`.
- If `icon` is provided, it must be a [Bootstrap Icons](https://icons.getbootstrap.com)
  name: lowercase letters, digits and hyphens, `<= 64` chars. A `bi-` prefix is
  accepted and stripped, so `"bi-terminal"` and `"terminal"` are equivalent.

Icons are optional in both directions. An event registered without one is
published with the default `terminal` glyph, and a server built before icons
existed ignores the field entirely — so upgrading either side alone is safe.

## Validation and Errors

The SDK fails fast with `IllegalArgumentException` for invalid input.

Validated inputs include:
- `connect`: host format, host length, protocol/path rejection, and port range `1..65535`.
- `connect` ID: allowed characters and max length.
- `registerEvent`: event ID pattern and options validation.
- `setState`: allowed states and message rules.
- Mutable fields: `hostname` and `appVersion` must be non-blank and size-limited.

### Ack

Provided to each event handler:
- `ack.success()` reports successful completion.
- `ack.error("reason")` reports failed completion.
- `ack.feedback("message")` optionally reports progress while the handler runs.
- `ack.isResolved()` is true once the ack has settled, however it settled.
- `ack.getStatus()` returns the `AckStatus`.

For `hasFeedback = false`, ack calls are harmless no-ops.

Every method is safe to call from any thread, and every method is a no-op once
the ack has resolved — a slow handler that finishes late cannot overwrite the
result the timeout already reported.

#### Progress feedback

`ack.feedback("message")` replaces the status text on this event's row in
ShowTrak's execution view, leaving the progress bar where it was. It is purely
optional: an event that never calls it behaves exactly as it always has.

```kotlin
ShowTrak.registerEvent(
	"RunDiagnostics",
	EventOptions(label = "Run Diagnostics", timeoutMs = 20_000)
) { ack ->
	backgroundExecutor.execute {          // never block the SDK's callback thread
		for (step in 1..5) {
			if (ack.isResolved()) return@execute   // timed out from under us
			doStep(step)
			ack.feedback("Step $step of 5 complete")
		}
		ack.success()
	}
}
```

Messages are trimmed and capped at 255 characters (longer ones are cut, not
rejected). Blank messages, calls after the ack has resolved, and calls on a
`hasFeedback = false` event are all ignored.

#### AckStatus

- `UNRESOLVED` — still running.
- `RESOLVED_SUCCESS` — `success()` was called.
- `RESOLVED_ERROR` — `error()` was called, or the handler threw.
- `RESOLVED_TIMEOUT` — the handler ran past `EventOptions.timeoutMs`.

A handler that has not resolved within `timeoutMs` is resolved for it, and the
timeout is reported to the server so the operator sees the event fail rather
than hang. The server arms its own watchdog a few seconds beyond `timeoutMs`,
so an event whose device disappears mid-run still settles.

### SDKStatus

`SDKStatus` is an immutable snapshot returned by `ShowTrak.getStatus()`.

Fields:
- `connectionState: ConnectionState`
- `connected: Boolean`
- `adopted: Boolean`
- `clientId: String?`
- `hostname: String`
- `appVersion: String`
- `desiredState: String`
- `degradedMessage: String?`
- `serverHost: String?`
- `serverPort: Int?`
- `registeredEventIds: List<String>`

## Minimal Integration Example

```kotlin
class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		ShowTrak.connect(applicationContext, ip = "10.0.2.2", port = 3000)

		ShowTrak.registerEvent(
			"SetBoxBlue",
			EventOptions("Set Box Blue", colour = 2, icon = "droplet-fill")
		) { ack ->
			runOnUiThread {
				// update UI
				ack.success()
			}
		}

		ShowTrak.onStateChanged { state ->
			runOnUiThread {
				// update connection indicator
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		ShowTrak.disconnect()
	}
}
```

## Networking Notes

- Emulator-to-host IP is typically `10.0.2.2`.
- SDK uses Socket.IO websocket transport.
- Heartbeat interval is 1 second.

## Protocol Reference

Full protocol and behavior reference:
- `SHOWTRAK_INTEGRATION_SDK.md`

## Usage in another project

From a consuming Gradle project (for example the demo app), include the module
as a project dependency and map it to this repository path:

```kotlin
include(":showtrak-sdk")
project(":showtrak-sdk").projectDir = file("../ShowTrak-SDK-Android/showtrak-sdk")
```

Then depend on it:

```kotlin
implementation(project(":showtrak-sdk"))
```

This keeps a single SDK source of truth shared across repos.

## Maven Central

The SDK is published to Maven Central:

```kotlin
repositories {
	google()
	mavenCentral()
}

dependencies {
	implementation("io.github.showtrak:showtrak-sdk:1.3.0")
}
```

## Part of ShowTrak

| App | What it does |
| --- | --- |
| **[ShowTrak Server](https://showtrak.co.uk/software/server)** | Monitor and control every computer and show device from one screen. |
| **[ShowTrak Client](https://showtrak.co.uk/software/client)** | The lightweight app that lets ShowTrak Server watch and control a computer. |
| **[ShowTrak Timers](https://showtrak.co.uk/software/timers)** | Show timers run from the desk, a Stream Deck, QLab or any browser. |
| **[ShowTrak Portal](https://showtrak.co.uk)** | Show reports, schedules and team collaboration for live productions. |

Find everything at **[showtrak.co.uk](https://showtrak.co.uk)**.

## Support

Questions, ideas or problems? Join the [ShowTrak Discord](https://discord.gg/DACmwsbSGW) or [open an issue](https://github.com/ShowTrak/ShowTrakClient-SDK-Android/issues).

[badge-android7]: https://img.shields.io/badge/Android%207.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white
[badge-kotlin]: https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white
