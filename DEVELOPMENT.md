# Couch Controls - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). No server-side installation needed. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

Couch Controls compiles against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts. The compile catches drift between the two trees, not against the older Pandorical a player may have installed; see Pandorical integration below for how that is handled at runtime.

```bash
./gradlew build
```

The Minecraft and Fabric API ranges in `fabric.mod.json` are filled in from `gradle.properties` at build time. Change the pin, never the manifest.

## How it works

**The pad is SDL, not GLFW.** Minecraft's windowing and input are SDL3: `Window` takes an `SDL_Event`,
and `InputConstants`' key codes are SDL scancodes rather than GLFW ones (`KEY_G` is 10, not 71). SDL's
gamepad API is therefore already loaded in the process: no native library to ship, no second input
backend, and rumble for free.

Gamepad events are switched **off** and state is polled with `SDL_UpdateGamepads`. Minecraft owns the
SDL event queue, and draining it here would eat input the game needs.

**Actions go through vanilla key mappings.** A bound button makes the vanilla `KeyMapping` for that
action report itself pressed, rather than calling game methods directly, so block-breaking progress,
bow and food charge-up and sneak's edge cases all run down the ordinary key path.

**Movement is the exception**, because a key is binary and a stick is not. Each tick, right after
vanilla builds `ClientInput.moveVector` from the keys, the stick's real deflection replaces the
keyboard's cardinal 1.0 there, and everything downstream (sneak scaling, slowdowns, the
square-movement correction) reads the analog value.

**Navigation moves the real pointer.** It draws no highlight of its own: the cursor is warped onto the
chosen target, so hover states, tooltips and every screen's existing mouse handling keep working. The
right stick moves the pointer freely for anything that cannot be enumerated. The pointer is only moved
on a frame the pad moved it, and the pad's cursor follows the mouse whenever the mouse has moved since;
the pad's first push seats it on the nearest slot to the middle.

**Which pad.** Every connected pad is held open, since a pad left charging may be the first one SDL
lists. The first found takes control; after 2 s idle, another takes it with a button, a trigger, or a
stick past half travel (higher than any deadzone, which is why deadzones stop at 40%). The log names
the pad in control and how many it can see.

## Pandorical integration

A soft dependency (`suggests`, `breaks` below 15.0.0).

Pandorical screens build their UI from server-sent component definitions that are not vanilla
widgets, so `Screen.children()` reports such a screen as empty. Pandorical's common API has
`NavigableScreen`, through which a screen advertises where its interactive regions are;
`PandoricalScreen` implements it by walking its component tree for components that opt in via
`PandoricalComponent.isNavigable()`. Regions are geometry only, with no activate hook, so one
mechanism drives vanilla slots, vanilla widgets and Pandorical components alike.
`PandoricalContainerScreen` extends the vanilla container screen, so its item slots were already
covered.

Keybinds: suite mods declare keys through Pandorical's pooled slots, which are ordinary `KeyMapping`s.
By default the d-pad drives pool indices 0-2 (down, left, right; index 2 is the notice tray, pre-bound
to N) and up opens the action menus (`PandoricalKeybinds`). The press travels to the server down
Pandorical's existing path.

The integration is compiled against the real interface, not reflection, so drift between the two trees
is a build error. Runtime isolation is by class-loading behind `CouchControls.PANDORICAL_LOADED`, so a
client without Pandorical never loads a class that names it, and API newer than the oldest published
Pandorical is probed before its first use.
