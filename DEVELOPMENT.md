# Couch Controls - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). No server-side installation needed. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

Couch Controls compiles against Pandorical's live source for `NavigableScreen`, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts. Pandorical is still only a *runtime* soft dependency; the compile-time one is what makes the integration drift into a build error instead of a silent no-op.

```bash
./gradlew build
```

The Minecraft and Fabric API ranges in `fabric.mod.json` are filled in from `gradle.properties` at build time. Change the pin, never the manifest.
