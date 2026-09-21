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

**Movement is the exception**, because a key is binary and a stick is not. Each tick, right after vanilla builds `ClientInput.moveVector` from the keys, the stick's real deflection replaces the keyboard's cardinal 1.0 there. Walking, sprint's start, and everything downstream of it (sneak scaling, slowdowns, the square-movement correction) then read the analog value.

**Navigation moves the real pointer.** It does not draw a highlight of its own. The cursor is warped onto the chosen target, so hover states, tooltips, item counts and every screen's existing mouse handling keep working: from the game's side, nothing unusual happened. The right stick still moves the pointer freely for anything that cannot be enumerated — scroll regions, maps, screens that draw their own controls.

**The mouse is never held hostage.** The pointer is only moved on a frame the pad moved it, and the pad's cursor follows the mouse whenever the mouse has moved since. A screen opens with the pointer where the mouse left it; the pad's first push on the stick seats it on the nearest slot to the middle, and from then on whichever hand moved last has it.

## Pandorical

A **soft** dependency. Everything above is vanilla, and this mod runs on any client.

Pandorical screens build their UI from server-sent component definitions that are deliberately not vanilla widgets, so `Screen.children()` reports such a screen as empty and a navigator finds nothing to press. Pandorical therefore grew a small addition here — `NavigableScreen`, in its common API — through which a screen advertises where its interactive regions are. `PandoricalScreen` implements it by walking its component tree for components that opt in via `PandoricalComponent.isNavigable()`.

Regions are geometry only, with no activate hook, and that is the point: one mechanism drives vanilla slots, vanilla widgets and Pandorical components alike.

The other half is keybinds. Suite mods declare their own keys through Pandorical's pooled slots rather than shipping client code, so those keys are ordinary `KeyMapping`s that nothing on a pad reaches by default. By default the d-pad drives pool slots 1-4, which is how poopsmith's poop key (slot 1, `G` on a keyboard) becomes d-pad down. The press travels to the server down Pandorical's existing path; there is no extra protocol.

Because Pandorical is the platform every suite screen is built on, this covers the suite by construction — and `PandoricalContainerScreen` extends the vanilla container screen, so its item slots were already covered by the vanilla path.

The integration is compiled against the real interface, **not** reflection. The suite already knows what string-keyed reflection costs (see the village web's `integration/` packages, which fail silently when a class is renamed); compiling against Pandorical's source turns drift between the two trees into a build error. That says nothing about the Pandorical a player installed, which may be older. Runtime isolation is by class-loading behind one flag, `CouchControls.PANDORICAL_LOADED`, so a client without Pandorical never loads a class that names it, and API newer than the oldest published Pandorical is probed before its first use.

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

Handing a second pad to somebody else and having them press A is therefore all it takes to switch.
That press only takes control; it does not also jump or click. The log names the pad it is listening
to and how many it can see.

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
| West (X) | Drop item |
| North (Y) | Open inventory |
| Shoulders | Previous / next hotbar slot |
| Left stick all the way forward | Sprint (latches until the stick recentres; afloat in deep water, it holds through the surface and dives at the next dip) |
| Left stick click | Sneak (a press toggles it when the Sneak option is set to Toggle) |
| East (B) | Chat, with the pointer on the newest link: A clicks it, so a teleport or trade request is B then A |
| Right stick click | Swap hands |
| Start | Pause menu (press again to close) |
| Back | Player list |
| D-pad | Pandorical keybinds 1-4 (down = slot 1, poopsmith's poop key) |

**Reeling a fish** is the one place the left trigger is read as more than a button. The fishing
fight (Minedew Fishing) is driven by right-clicks, one kick upward per click, and a mouse plays it by
tapping at a rate. While a bobber is out, a tap of the trigger is still one click, and a hold clicks
on its own: two a second at the lightest squeeze, eight fully pulled. Pull harder to rise, ease off to
sink, let go to drop.

### In menus

| Control | Action |
|---|---|
| Left stick / D-pad | Step between slots and buttons |
| Right stick | Move the pointer freely |
| South (A) | Click |
| West (X) | Right click |
| North (Y) | Shift-click (quick move); closes your own inventory |
| East (B) | Close (whatever Escape would close) |
| Start | Close |
| Shoulders | Scroll (bundle contents, long lists) |

## Changing the layout

The world layout above is the default. **Options > Controls > Key Binds** has a controller column
beside the keyboard one, and every row in it can take a pad control: vanilla's actions, other mods'
keys, and Pandorical's pooled slots alike. A pad control can drive anything the game reads as a
held or clicked key, so binding one to *Toggle Perspective* or *Hotbar Slot 3* simply works.

Click a row's controller button (with A, or the mouse) and press the control you want. **Start
clears it**, the way Escape clears a key, and a click or a key press stops waiting without changing
anything. Reset puts the row back to its defaults, pad and keyboard both, and Reset Keys does the
same for every row. A control on two actions is marked the way a keyboard clash is, and both fire.

Hotbar stepping appears as two rows of its own under Inventory, *Previous Hotbar Slot* and *Next
Hotbar Slot*, unbound on the keyboard until given a key.

Some rows show `-`: screenshot, fullscreen, friends and the debug keys are read straight off the
keyboard event, so no pad control can reach them.

Fixed, and not in the list: the stick controls (move, look, sprint), Start as pause, and the whole
menu layout. The menu layout is what gets you into this screen in the first place, so it is not
something one wrong binding should be able to take away.

Changes are saved to `config/couch-controls/pad-binds.properties`, only where they differ from
the defaults.

## Typing

Clicking into a text box with the pad (A on chat's input, an anvil's name, a book's page, a command
block, the creative search) opens an on-screen keyboard; a sign opens it as soon as the sign is
placed, and A anywhere on the sign that is not the Done button brings it back. It sits above the
text when the text is low on the screen, as chat's is, and below it otherwise.

| Control | Action |
|---|---|
| Left stick / D-pad | Move between keys (across the edges, as a phone's does) |
| South (A) | Press the key |
| West (X) | Delete (hold to keep deleting) |
| North (Y) | Space |
| Left stick click | Shift, for one letter |
| Shoulders | Move the text cursor |
| Start | Enter (sends chat; next line on a sign) |
| East (B) | Close the keyboard, leaving the screen open |

`?123` swaps in numbers and symbols. The keys go to the screen exactly as a keyboard's do, so what
the text box accepts is up to the text box. Touching the real keyboard or mouse closes it.

## Rumble

The pad shakes when you are hurt (harder the more it took, and a long one when it kills you), when
a hit lands, when a block breaks, when something bites your line, and when an explosion goes off
near you, fading with distance. The bite is vanilla's own bite flag, which Minedew Fishing sets too.

Only while the pad is the hands in use: a key or a mouse button hands the game back to the keyboard
and the pad goes quiet until it is touched again, so a pad left charging on the shelf never buzzes.

Rumble can be switched off and its strength set in the settings below; changing either gives a
sample shake so it can be judged by hand.

## Settings

On Couch Controls' page in Pandorical's mod menu, and in `config/couch-controls/settings.properties`,
which works with or without Pandorical. The menu writes the file, so the two never disagree; the
page needs a Pandorical recent enough to have client settings.

| Setting | File key | Default | Range |
|---|---|---|---|
| Look speed | `look_speed` | 100% (220 degrees a second at full push) | 25-300% |
| Invert look | `invert_look` | off | |
| Pointer speed, in menus | `pointer_speed` | 100% (500 GUI pixels a second) | 25-300% |
| Left stick deadzone | `left_deadzone` | 18% of the stick's travel | 4-40% |
| Right stick deadzone | `right_deadzone` | 18% of the stick's travel | 4-40% |
| Rumble | `rumble` | on | |
| Rumble strength | `rumble_strength` | 100% | 10-100% |

Each stick has its own deadzone, since sticks wear unevenly and the camera shows drift that walking
hides. It is radial, and the stick past it is rescaled, so full push is still full speed whatever
the deadzone. A worn stick that drifts wants it higher; a new one can go lower for finer aim. It stops at 40% because a pad waiting to take over needs a half push, and a
resting stick must never reach that.

## Known gaps

- **Text boxes that are not vanilla's.** The on-screen keyboard types into vanilla's text boxes and anything built on them; a mod that draws its own text entry, Pandorical's server-declared screens included, still needs a keyboard.
- **Creative inventory tabs** are widgets and so are reachable, but the tab strip navigates awkwardly.

## Development

Installing and building are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
