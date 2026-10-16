# Storyboard Displays

Storyboard Displays is a client-side Fabric mod for Minecraft 26.1.2 that puts
high-resolution PNG artwork and Minecraft-style title cards into the world.
Displays are depth-tested world geometry, so entities and the player can walk
in front of them. They are intentionally client-side and local, which makes
placing them while watching a Flashback replay possible without changing the
replay or requiring a server-side install.

## In-game workflow

1. Press **`O`** to open the director/editor.
2. Enter either a local PNG path or title text. The image path accepts an
   absolute path or a path relative to `.minecraft/storyboard-displays/`.
3. Adjust width, height, text size, color, and whether the display is lit.
4. Aim at the desired location and click **Place form at crosshair**.
5. Use the **Display Library** to select, rename, hide/show, duplicate, or
   delete displays. Clicking display rows toggles multi-selection; group
   movement and deletion are supported.
6. The inspector includes a live preview. Drag its lower-right handle to resize
   precisely, or place the mouse over the inspector and use the wheel to resize;
   hold **Shift** while scrolling to adjust text scale. Drag across display
   rows to select a group.
7. Press **`G`** to cycle the in-world gizmo through **off**, **move**, **scale**,
   and **rotate**. Hold left mouse and drag over the world to use the selected
   tool. In scale mode, **Ctrl** allows independent width/height changes;
   otherwise the aspect ratio is preserved. **Shift** snaps movement and size
   to half-block increments and rotation to 45-degree angles. The cyan
   rectangle and corner handles show the selected display.
8. Press **`N`** to cycle selection. Use **`P`** to unlock or lock the mouse
   for gizmo editing. The **Global visibility** control in the `O` menu hides
   or shows all displays.
9. The Transform tab can switch gizmo space between **Player** and **Display**.
   Player space follows the direction the player is facing, including diagonal
   facing; Display space follows the selected display's facing.

The PNG loader keeps original image dimensions, uses linear texture filtering,
and renders artwork full-bright, so large source artwork stays smooth instead
of becoming a pixelated or shadowed block texture. Displays are saved under
`.minecraft/storyboard-displays/displays.json`; press the **Clear all
displays** button when starting a different replay.

## Building

Requires Java 25 and Gradle 9.7 or newer:

```text
gradle build
```

The current release is **1.3.2**, producing
`build/libs/storyboard-displays-1.3.2.jar`. Future feature/fix builds will
increment the patch number (1.2.1, 1.2.2, and so on). Install it with Fabric Loader 0.19.5
or newer, Fabric API for Minecraft 26.1.2, and Mod Menu (optional).
