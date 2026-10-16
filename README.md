# Storyboard Displays

Storyboard Displays is a client-side Fabric mod for Minecraft 26.1.2 that puts
high-resolution PNG artwork and Minecraft-style title cards into the world.
Displays are depth-tested world geometry, so entities and the player can walk
in front of them. They are intentionally client-side and local, which makes
placing them while watching a Flashback replay possible without changing the
replay or requiring a server-side install.

## In-game workflow

1. Press **`O`** to open the editor.
2. Enter either a local PNG path or title text. The image path accepts an
   absolute path or a path relative to `.minecraft/storyboard-displays/`.
3. Adjust width, height, text size, color, and whether the display is lit.
4. Aim at the desired location and click **Place at crosshair**.
5. Use **`P`** to toggle all displays, and **`O`** to make another display.

The PNG loader keeps original image dimensions, uses linear texture filtering,
and renders artwork full-bright, so large source artwork stays smooth instead
of becoming a pixelated or shadowed block texture. Displays are saved under
`.minecraft/storyboard-displays/displays.json`; press the **Clear local
displays** button when starting a different replay.

## Building

Requires Java 25 and Gradle 9.7 or newer:

```text
gradle build
```

The resulting jar is in `build/libs/`. Install it with Fabric Loader 0.19.5
or newer, Fabric API for Minecraft 26.1.2, and Mod Menu (optional).
