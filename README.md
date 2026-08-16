# Couch Controls

Native gamepad support for Minecraft Java: analog movement, stick camera, and menu navigation that steps between real slots and buttons instead of shoving a pointer at them.

Client-side only. The server neither needs it nor knows about it.

## Why this exists rather than Steam Input

Mapping a pad to keyboard and mouse at the OS level gets you most of the way through *playing* — Steam's joystick-mouse mode has real response curves, and looking around feels fine. It falls apart in menus, because there is nothing to snap to: you drive a free-floating pointer onto a 16x16 slot, from a couch, on a 4K TV, for every chest and furnace and crafting grid.

That is the half this does and an emulator structurally cannot. The game is told where its slots and buttons actually are, so a flick of the stick lands dead centre on the next one.

It also exists because the alternatives do not build here. Controlify and MidnightControls target release versions; this suite tracks Minecraft snapshots, and a mod that moves when the suite moves is worth more than one that has to wait for upstream.

## How it works

**The pad is SDL, not GLFW.** Minecraft 26.3 moved its windowing and input to SDL3 — `Window` takes an `SDL_Event`, and `InputConstants`' key codes are SDL scancodes rather than GLFW ones (`KEY_G` is 10, not 71). SDL's gamepad API is therefore already loaded in the process: no native library to ship, no second input backend, and rumble available for free.

Gamepad events are switched **off** and state is polled with `SDL_UpdateGamepads`. Minecraft owns the SDL event queue and draining it here would eat input the game needs.

**Actions go through vanilla key mappings.** A bound button makes the vanilla `KeyMapping` for that action report itself pressed, rather than calling game methods directly. Block-breaking progress and cooldown, bow and food charge-up, sneak's edge cases — all of it then runs down the ordinary key path, unmodified. None of it has to be re-derived and none of it can drift out of sync with vanilla, because it *is* vanilla.

**Movement is the exception**, because a key is binary and a stick is not. `LocalPlayer.applyInput` funnels the whole movement vector through `modifyInput` before splitting it into strafe and forward, which makes that the one place a stick's real deflection can replace the keyboard's cardinal 1.0. Everything downstream — sneak scaling, slowdowns, the square-movement correction — still applies.

**Navigation moves the real pointer.** It does not draw a highlight of its own. The cursor is warped onto the chosen target, so hover states, tooltips, item counts and every screen's existing mouse handling keep working: from the game's side, nothing unusual happened. The right stick still moves the pointer freely for anything that cannot be enumerated — scroll regions, maps, screens that draw their own controls.

## Pandorical

A **soft** dependency. Everything above is vanilla, and this mod runs on any client.

Pandorical screens build their UI from server-sent component definitions that are deliberately not vanilla widgets, so `Screen.children()` reports such a screen as empty and a navigator finds nothing to press. Pandorical therefore grew a small addition here — `NavigableScreen`, in its common API — through which a screen advertises where its interactive regions are. `PandoricalScreen` implements it by walking its component tree for components that opt in via `PandoricalComponent.isNavigable()`.

Regions are geometry only, with no activate hook, and that is the point: one mechanism drives vanilla slots, vanilla widgets and Pandorical components alike.

Because Pandorical is the platform every suite screen is built on, this covers the suite by construction — and `PandoricalContainerScreen` extends the vanilla container screen, so its item slots were already covered by the vanilla path.

The integration is compiled against the real interface, **not** reflection. The suite already knows what string-keyed reflection costs (see the village web's `integration/` packages, which fail silently when a class is renamed); a compile-checked interface turns that same drift into a build error. Runtime isolation is by class-loading: the flag lives in `Targets`, so a client without Pandorical never loads the class that names it.

## Layout

Positional names (SDL calls them SOUTH/EAST/WEST/NORTH, not A/B/X/Y), so this comes out right on an Xbox pad, a PlayStation pad, and the 8BitDo alike.

### In the world

| Control | Action |
|---|---|
| Left stick | Move (analog) |
| Right stick | Look |
| Right trigger | Attack / break |
| Left trigger | Use / place |
| South (A) | Jump |
| East (B) | Sneak |
| West (X) | Drop item |
| North (Y) | Open inventory |
| Shoulders | Cycle hotbar |
| Left stick click | Sprint (latches until the stick recentres) |
| Right stick click | Swap hands |
| Start | Pause menu (press again to close) |
| Back | Player list |

### In menus

| Control | Action |
|---|---|
| Left stick / D-pad | Step between slots and buttons |
| Right stick | Move the pointer freely |
| South (A) | Click |
| West (X) | Right click |
| North (Y) | Shift-click (quick move) |
| East (B) | Close |
| Start | Close |

Bindings are hardcoded for now. A rebinding UI is a real want, but it is a screen you would have to navigate before you can navigate screens, and the layout has to be usable before any of that exists.

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). No server-side installation needed. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

Couch Controls compiles against Pandorical's live source for `NavigableScreen`, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts. Pandorical is still only a *runtime* soft dependency; the compile-time one is what makes the integration drift into a build error instead of a silent no-op.

```bash
./gradlew build
```

The Minecraft dependency is `>=26.3-alpha.1`, not the suite's usual `>=26.2`: SDL input is verified present on the 26.3 snapshots and nowhere earlier. **Write it as `alpha`, not `snapshot`** — Fabric normalizes `26.3-snapshot-8` to the semver prerelease `26.3-alpha.8`, so a predicate written against the Mojang spelling silently matches nothing and the mod refuses to load on the exact version it targets. The build stays green either way; only a launch catches it.

## Known gaps

- **Text entry.** Chat, signs, anvils and command blocks still need a keyboard. Landing on a text field focuses it; typing into it is a separate problem that wants an on-screen keyboard.
- **No rumble yet.** SDL exposes it and `Gamepad.rumble` is wired, but nothing calls it.
- **No dead zone, sensitivity or binding configuration.** All tuning constants are in the source, each with a note on what moving it costs.
- **Creative inventory tabs** are widgets and so are reachable, but the tab strip navigates awkwardly.
- **First pad only.** `SDL_GetGamepads` returns a list and this opens index 0.

## License

MIT, see [LICENSE](LICENSE).
