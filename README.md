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

**The mouse is never held hostage.** The pointer is only moved on a frame the pad moved it, and the pad's cursor follows the mouse whenever the mouse has moved since. A screen opens with the pointer where the mouse left it; the pad's first push on the stick seats it on the nearest slot to the middle, and from then on whichever hand moved last has it.

## Pandorical

A **soft** dependency. Everything above is vanilla, and this mod runs on any client.

Pandorical screens build their UI from server-sent component definitions that are deliberately not vanilla widgets, so `Screen.children()` reports such a screen as empty and a navigator finds nothing to press. Pandorical therefore grew a small addition here — `NavigableScreen`, in its common API — through which a screen advertises where its interactive regions are. `PandoricalScreen` implements it by walking its component tree for components that opt in via `PandoricalComponent.isNavigable()`.

Regions are geometry only, with no activate hook, and that is the point: one mechanism drives vanilla slots, vanilla widgets and Pandorical components alike.

The other half is keybinds. Suite mods declare their own keys through Pandorical's pooled slots rather than shipping client code, so those keys are ordinary `KeyMapping`s that nothing on a pad reaches by default. The d-pad drives pool slots 1-4, which is how poopsmith's poop key (slot 1, `G` on a keyboard) becomes d-pad down. The press travels to the server down Pandorical's existing path; there is no extra protocol.

Because Pandorical is the platform every suite screen is built on, this covers the suite by construction — and `PandoricalContainerScreen` extends the vanilla container screen, so its item slots were already covered by the vanilla path.

The integration is compiled against the real interface, **not** reflection. The suite already knows what string-keyed reflection costs (see the village web's `integration/` packages, which fail silently when a class is renamed); a compile-checked interface turns that same drift into a build error. Runtime isolation is by class-loading: the flag lives in `Targets`, so a client without Pandorical never loads the class that names it.

## Which pad, when there are two

A controller left plugged in to charge is still a controller as far as SDL is concerned, and it may
well be the first one listed. Binding blind to that one looks *exactly* like working - the log says
a controller connected, and nothing responds.

So the pad in charge is the pad being used. Every connected pad is held open; the first one found
takes control at startup, since nobody is touching anything yet, and keeps it for as long as it is
being used. Once it has been idle a couple of seconds, any other pad gets it by asking: a button, a
trigger, or a stick pushed further than a resting stick ever sits. Buttons and triggers are taken at
face value because they cannot drift; the sticks are held to a higher bar than the deadzone so a
worn pad on a shelf can never take the game away from the one in somebody's hands.

Handing a second pad to somebody else and having them press A is therefore all it takes to switch,
and the log names the pad it is listening to and how many it can see.

## Layout

Positional names (SDL calls them SOUTH/EAST/WEST/NORTH, not A/B/X/Y), so this comes out right on an Xbox pad, a PlayStation pad, and the 8BitDo alike.

### In the world

| Control | Action |
|---|---|
| Left stick | Move (analog) |
| Right stick | Look |
| Right trigger | Attack / break |
| Left trigger | Use / place |

**Reeling a fish** is the one place the left trigger is read as more than a button. The fishing
fight (Minedew Fishing) is driven by right-clicks, one kick upward per click, and a mouse plays it by
tapping at a rate. While a bobber is out, a tap of the trigger is still one click, and a hold clicks
on its own: two a second at the lightest squeeze, eight fully pulled. Pull harder to rise, ease off to
sink, let go to drop.
| South (A) | Jump |
| East (B) | Sneak |
| West (X) | Drop item |
| North (Y) | Open inventory |
| Shoulders | Cycle hotbar |
| Left stick click | Sprint (latches until the stick recentres) |
| Right stick click | Swap hands |
| Start | Pause menu (press again to close) |
| Back | Player list |
| D-pad | Pandorical keybinds 1-4 (down = slot 1, poopsmith's poop key) |

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
| Shoulders | Scroll (bundle contents, long lists) |

Bindings are hardcoded for now. A rebinding UI is a real want, but it is a screen you would have to navigate before you can navigate screens, and the layout has to be usable before any of that exists.

## Known gaps

- **Text entry.** Chat, signs, anvils and command blocks still need a keyboard. Landing on a text field focuses it; typing into it is a separate problem that wants an on-screen keyboard.
- **No rumble yet.** SDL exposes it and `Gamepad.rumble` is wired, but nothing calls it.
- **No dead zone, sensitivity or binding configuration.** All tuning constants are in the source, each with a note on what moving it costs.
- **Creative inventory tabs** are widgets and so are reachable, but the tab strip navigates awkwardly.
- **First pad only.** `SDL_GetGamepads` returns a list and this opens index 0.

## Development

Installing and building are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
