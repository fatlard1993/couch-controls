# Couch Controls

Native gamepad support for Minecraft Java: analog movement, stick camera, and menu navigation that steps between real slots and buttons instead of pushing a pointer at them. That menu snapping is what mapping a pad to keyboard and mouse with Steam Input cannot do.

## Layout

Buttons are named by position (South, East, West, North), so the layout is the same on an Xbox pad, a PlayStation pad and an 8BitDo. The letters in brackets are Xbox's.

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
| Left stick all the way forward | Sprint (latches until the stick recentres; not in water, where pushing forward wades rather than dives) |
| Left stick flicked forward twice, in water | Swim fast: full forward, back toward centre, full forward again within 350 ms. Ends as a vanilla swim does |
| Left stick click | Sneak (a press toggles it when the Sneak option is set to Toggle) |
| East (B) | Chat, with the pointer on the newest link: A clicks it, so a teleport or trade request is B then A |
| Right stick click | Swap hands |
| Start | Pause menu (press again to close) |
| Back | Player list |
| D-pad | With Pandorical: up opens the action menus, down and left are pooled keybind slots 1 and 2, right is slot 3 (the notice tray). Without it, nothing |

**Reeling a fish** (Minedew Fishing's fight, played by repeated right-clicks): while a bobber is out, a tap of the left trigger is one click and a hold clicks on its own, two a second at the lightest squeeze up to eight fully pulled. Pull harder to rise, ease off to sink.

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

The mouse works alongside the pad: whichever moved last has the pointer.

## Changing the layout

The world layout above is the default. **Options > Controls > Key Binds** has a controller column beside the keyboard one, and every row in it can take a pad control: vanilla's actions, other mods' keys, and Pandorical's pooled slots alike. Binding one to *Toggle Perspective* or *Hotbar Slot 3* works like any key.

Click a row's controller button (with A, or the mouse) and press the control you want. **Start clears it**, the way Escape clears a key, and a click or a key press stops waiting without changing anything. Reset puts the row back to its defaults, pad and keyboard both, and Reset Keys does the same for every row. A control on two actions is marked the way a keyboard clash is, and both fire.

Hotbar stepping appears as two rows of its own under Inventory, *Previous Hotbar Slot* and *Next Hotbar Slot*, unbound on the keyboard until given a key.

Some rows show `-`: screenshot, fullscreen, friends and the debug keys are read straight off the keyboard event, so no pad control can reach them.

Fixed, and not in the list: the stick controls (move, look, sprint), Start as pause, and the whole menu layout, so no binding can lock you out of this screen.

Changes are saved to `config/couch-controls/pad-binds.properties`, only where they differ from the defaults.

## Typing

Clicking into a text box with the pad (A on chat's input, an anvil's name, a book's page, a command block, the creative search) opens an on-screen keyboard; a sign opens it as soon as the sign is placed, and A anywhere on the sign that is not the Done button brings it back. It sits above the text when the text is low on the screen, as chat's is, and below it otherwise.

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

`?123` swaps in numbers and symbols. The keys go to the screen exactly as a keyboard's do, so what the text box accepts is up to the text box. Touching the real keyboard or mouse closes it.

## Rumble

The pad shakes when you are hurt (harder the more it took, and a long one when it kills you), when a hit lands, when a block breaks, when something bites your line, and when an explosion goes off near you, fading with distance. Minedew Fishing's bites count.

Only while the pad is in use: after a key or mouse press it stays quiet until it is touched again.

Rumble can be switched off and its strength set in the settings below; changing either gives a sample shake.

## Settings

On Couch Controls' page in Pandorical's mod menu, and in `config/couch-controls/settings.properties`, which works with or without Pandorical. The menu writes the file, so the two never disagree; the page needs a Pandorical recent enough to have client settings.

| Setting | File key | Default | Range |
|---|---|---|---|
| Look speed | `look_speed` | 100% (220 degrees a second at full push) | 25-300% |
| Invert look | `invert_look` | off | |
| Pointer speed, in menus | `pointer_speed` | 100% (500 GUI pixels a second) | 25-300% |
| Left stick deadzone | `left_deadzone` | 18% of the stick's travel | 4-40% |
| Right stick deadzone | `right_deadzone` | 18% of the stick's travel | 4-40% |
| Rumble | `rumble` | on | |
| Rumble strength | `rumble_strength` | 100% | 10-100% |

The deadzones are radial and the stick past them is rescaled, so full push is still full speed. Raise one for a worn stick that drifts; lower it on a new one for finer aim.

## Two pads

The first pad found takes control at startup and keeps it while it is in use. Once it has been idle for two seconds, any other pad takes over with a button, a trigger, or a stick pushed at least halfway. That press only takes control; it does not also jump or click. So a pad left plugged in to charge cannot steal the game, and handing a second pad to somebody else and having them press A is all it takes to switch.

## Known gaps

- **Text boxes that are not vanilla's.** The on-screen keyboard types into vanilla's text boxes and anything built on them; a mod that draws its own text entry, Pandorical's server-declared screens included, still needs a keyboard.
- **Creative inventory tabs** are widgets and so are reachable, but the tab strip navigates awkwardly.

## Pandorical

Optional. With Pandorical installed (15.0.0 or later), the pad also steps through Pandorical's screens, the d-pad reaches its action menus and keybind slots, and the settings appear on Couch Controls' page in its mod menu.

## Installing

Client-side only: put it in your client's `mods` folder. Servers do not need it and cannot tell it is there, so it works on any server, vanilla included, and in singleplayer.

## Development

Installing and building are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
