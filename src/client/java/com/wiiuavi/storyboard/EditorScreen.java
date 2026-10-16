package com.wiiuavi.storyboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public final class EditorScreen extends Screen {
	private EditBox name;
	private EditBox image;
	private EditBox text;
	private EditBox widthField;
	private EditBox heightField;
	private EditBox textScale;
	private EditBox color;
	private Direction facing = Direction.NORTH;
	private boolean placingNew;
	private int scrollOffset;
	private int tab;
	private Button gizmoModeButton;

	public EditorScreen() {
		super(Component.literal("Storyboard Displays • Director"));
	}

	@Override
	protected void init() {
		int left = 24;
		int panelWidth = Math.min(300, this.width / 3);
		int right = left + panelWidth + 18;
		Display selected = StoryboardStore.selected();
		if (selected != null) facing = selected.facing;

		addRenderableWidget(Button.builder(Component.literal("DISPLAYS"), button -> switchTab(0))
				.bounds(left, 32, 140, 24).build());
		addRenderableWidget(Button.builder(Component.literal("TRANSFORM"), button -> switchTab(1))
				.bounds(left + 148, 32, 140, 24).build());
		if (tab == 0) {
			addRenderableWidget(button("New display", "Start a new display from the current form.", left, 78));
			addRenderableWidget(button("Select all visible", "Select every display that is currently visible.", left, 108));
			addRenderableWidget(button("Delete selected", "Delete every selected display.", left, 138));
			addRenderableWidget(button("Duplicate selected", "Create an offset copy of the selected display.", left, 168));
			addRenderableWidget(button("Hide / show selected", "Temporarily hide or show the selected displays.", left, 198));
			addRenderableWidget(button("Clear all displays", "Delete every saved display.", left, 228));
			addRenderableWidget(Button.builder(Component.literal("Global visibility: "
					+ (StoryboardStore.isVisible() ? "ON" : "OFF")), ignored -> {
				StoryboardStore.setVisible(!StoryboardStore.isVisible());
				refresh();
			}).bounds(left, 258, 280, 22).build());

			int y = 305 - scrollOffset;
			for (Display display : StoryboardStore.displays()) {
				addRenderableWidget(Button.builder(Component.literal(
						(StoryboardStore.isSelected(display) ? "● " : "○ ")
								+ (display.hidden ? "[hidden] " : "") + display.name),
						button -> {
							StoryboardStore.toggleSelected(display);
							refresh();
						}).bounds(left, y, panelWidth, 22).build());
				y += 26;
			}
			addForm(right, selected);
		} else {
			addTransformPanel(left, right, selected);
		}
	}

	private Button button(String label, String tooltip, int x, int y) {
		Button button = Button.builder(Component.literal(label), ignored -> {
			switch (label) {
				case "New display" -> {
					StoryboardStore.select(null);
					refresh();
				}
				case "Select all visible" -> {
					StoryboardStore.selectAll();
					refresh();
				}
				case "Delete selected" -> {
					StoryboardStore.removeSelected();
					refresh();
				}
				case "Duplicate selected" -> {
					StoryboardStore.duplicate(StoryboardStore.selected());
					refresh();
				}
				case "Hide / show selected" -> {
					for (Display display : StoryboardStore.selectedDisplays()) display.hidden = !display.hidden;
					StoryboardStore.save();
					refresh();
				}
				case "Clear all displays" -> {
					StoryboardStore.clear();
					refresh();
				}
			}
		}).bounds(x, y - scrollOffset, 280, 22).build();
		button.setTooltip(Tooltip.create(Component.literal(tooltip)));
		return button;
	}

	private void addForm(int x, Display selected) {
		int y = 64 - scrollOffset;
		name = addField(x, y + 34, "Display name", "A friendly name shown in the display list.",
				selected == null ? "Untitled display" : selected.name, 256);
		image = addField(x, y + 90, "Image file", "PNG path, absolute or relative to .minecraft/storyboard-displays.",
				selected == null ? "" : selected.imagePath, 1024);
		text = addField(x, y + 146, "Title text", "Optional Minecraft-style text drawn on the display.",
				selected == null ? "" : selected.text, 512);
		widthField = addField(x, y + 202, "Width in blocks", "How wide the display is in the world.",
				selected == null ? "4.0" : Float.toString(selected.width), 32);
		heightField = addField(x, y + 258, "Height in blocks", "How tall the display is; image width follows its aspect ratio when placing.",
				selected == null ? "2.25" : Float.toString(selected.height), 32);
		textScale = addField(x, y + 314, "Text scale", "Multiplier for the optional title text.",
				selected == null ? "1.0" : Float.toString(selected.textScale), 32);
		color = addField(x, y + 370, "ARGB color", "Tint for text and fallback artwork, such as FFFFFFFF.",
				selected == null ? "FFFFFFFF" : Integer.toHexString(selected.color), 16);
		addRenderableWidget(Button.builder(Component.literal("Rotate 90°  [" + facing.getName() + "]"), button -> {
			facing = switch (facing) {
				case NORTH -> Direction.EAST;
				case EAST -> Direction.SOUTH;
				case SOUTH -> Direction.WEST;
				default -> Direction.NORTH;
			};
		}).bounds(x, y + 426, 280, 22).build());
		addRenderableWidget(Button.builder(Component.literal(selected == null ? "Place at crosshair" : "Apply changes"),
				button -> apply(selected)).bounds(x, y + 458, 135, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Place another"),
				button -> placeNew()).bounds(x + 145, y + 458, 135, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds(x, y + 490, 280, 22).build());
	}

	private void addTransformPanel(int left, int right, Display selected) {
		int y = 78;
		addRenderableWidget(button("Select next", "Select the next visible display.", left, y));
		addRenderableWidget(button("Select all visible", "Select all displays for group movement.", left, y + 30));
		addRenderableWidget(button("Delete selected", "Delete the selected display group.", left, y + 60));
		gizmoModeButton = Button.builder(Component.literal(gizmoLabel()), button -> {
			TransformController.cycleMode();
			refresh();
		}).bounds(left, y + 100, 280, 22).build();
		gizmoModeButton.setTooltip(Tooltip.create(Component.literal(
				"Cycle Off, Move, Scale, and Rotate. Press G outside the menu.")));
		addRenderableWidget(gizmoModeButton);
		addRenderableWidget(Button.builder(Component.literal("Gizmo space: " + TransformController.space().name()),
				button -> {
					TransformController.toggleSpace();
					refresh();
				}).bounds(left, y + 130, 280, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Rotate 90°"), button -> rotateSelected())
				.bounds(left, y + 160, 280, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds(left, y + 196, 280, 22).build());
		if (selected != null) {
			addForm(right, selected);
		} else {
			addRenderableWidget(Button.builder(Component.literal("Place a display first"),
					button -> switchTab(0)).bounds(right, y, 280, 22).build());
		}
	}

	private static String gizmoLabel() {
		return "Gizmo: " + TransformController.mode().name() + "  [G]";
	}

	private EditBox addField(int x, int y, String label, String tooltip, String value, int maxLength) {
		EditBox box = new EditBox(font, x, y, 280, 22, Component.literal(label));
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setHint(Component.literal(label));
		box.setTooltip(Tooltip.create(Component.literal(tooltip)));
		addRenderableWidget(box);
		return box;
	}

	private void rotateSelected() {
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.facing = switch (display.facing) {
				case NORTH -> Direction.EAST;
				case EAST -> Direction.SOUTH;
				case SOUTH -> Direction.WEST;
				default -> Direction.NORTH;
			};
		}
		StoryboardStore.save();
		refresh();
	}

	private void apply(Display display) {
		if (display == null) {
			placeNew();
			return;
		}
		display.name = name.getValue().isBlank() ? "Untitled display" : name.getValue();
		display.imagePath = image.getValue().trim();
		display.text = text.getValue();
		display.width = number(widthField.getValue(), display.width);
		display.height = number(heightField.getValue(), display.height);
		if (placingNew && !display.imagePath.isBlank()) {
			display.width = display.height * DisplayRenderer.imageAspectRatio(display.imagePath);
		}
		display.textScale = number(textScale.getValue(), display.textScale);
		display.color = parseColor(color.getValue(), display.color);
		display.facing = facing;
		StoryboardStore.save();
		refresh();
	}

	private void placeNew() {
		Display display = StoryboardStore.createAtCrosshair();
		if (display == null) return;
		StoryboardStore.select(display);
		placingNew = true;
		apply(display);
		placingNew = false;
	}

	private void switchTab(int value) {
		tab = value;
		scrollOffset = 0;
		refresh();
	}

	private void refresh() {
		if (minecraft != null) {
			minecraft.setScreen(this);
		}
	}

	private static float number(String value, float fallback) {
		try {
			return Math.max(0.05f, Float.parseFloat(value));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static int parseColor(String value, int fallback) {
		try {
			return (int) Long.parseLong(value.replace("#", ""), 16);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(14, 10, this.width - 14, this.height - 10, 0xCC10151E);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.text(font, "Choose a tab. Hover controls for an explanation. Scroll over a panel for more options.",
				24, 62, 0xFFB8C9D9);
		graphics.text(font, tab == 0 ? "DISPLAYS • library and artwork" : "TRANSFORM • position and orientation",
				this.width - 330, 42, 0xFF80C8FF);
		graphics.text(font, "G cycles gizmo • P unlocks/locks mouse • Player/Display space • Shift snaps • Ctrl unlocks ratio",
				24, this.height - 18, 0xFF9AA8B5);
		if (TransformController.active() && minecraft != null) {
			graphics.text(font, "GIZMO: " + TransformController.mode().name()
					+ "  •  HOVER: " + TransformController.hoveredHandle(minecraft).name()
					+ "  •  MOUSE: " + (TransformController.mouseUnlocked() ? "UNLOCKED" : "LOCKED"),
					24, this.height - 34, 0xFFFFE066);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX < this.width - 20) {
			scrollOffset = Math.max(0, Math.min(700, scrollOffset - (int) (vertical * 32)));
			clearWidgets();
			init();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
