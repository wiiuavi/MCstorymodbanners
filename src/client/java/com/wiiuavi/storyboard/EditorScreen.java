package com.wiiuavi.storyboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EditorScreen extends Screen {
	private EditBox image;
	private EditBox text;
	private EditBox widthField;
	private EditBox height;
	private EditBox scale;
	private Display editing;

	public EditorScreen() {
		super(Component.literal("Storyboard Displays"));
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 140;
		image = addRenderableWidget(new EditBox(font, left, 55, 280, 20, Component.literal("PNG path")));
		image.setMaxLength(1024);
		image.setHint(Component.literal("PNG path (optional)"));
		text = addRenderableWidget(new EditBox(font, left, 85, 280, 20, Component.literal("Title text")));
		text.setMaxLength(512);
		text.setHint(Component.literal("Title text (optional)"));
		widthField = addRenderableWidget(field(left, 125, "Width", "4.0"));
		height = addRenderableWidget(field(left, 155, "Height", "2.25"));
		scale = addRenderableWidget(field(left, 185, "Text size", "1.0"));
		addRenderableWidget(Button.builder(Component.literal("Place at crosshair"), button -> place())
				.bounds(left, 220, 135, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear local displays"), button -> {
			StoryboardStore.clear();
			onClose();
		}).bounds(left + 145, 220, 135, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(left, 250, 280, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Log image diagnostics"), button ->
				DisplayRenderer.logImageDiagnostics(image.getValue().trim()))
				.bounds(left, 280, 280, 20).build());
	}

	private EditBox field(int x, int y, String hint, String value) {
		EditBox box = new EditBox(font, x, y, 280, 20, Component.literal(hint));
		box.setValue(value);
		box.setHint(Component.literal(hint));
		return box;
	}

	private void place() {
		Display display = StoryboardStore.createAtCrosshair();
		if (display == null) return;
		display.imagePath = image.getValue().trim();
		display.text = text.getValue();
		display.width = number(widthField.getValue(), 4.0f);
		display.height = number(height.getValue(), 2.25f);
		display.textScale = number(scale.getValue(), 1.0f);
		StoryboardStore.save();
		editing = display;
	}

	private static float number(String value, float fallback) {
		try {
			return Math.max(0.05f, Float.parseFloat(value));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, this.width / 2, 25, 0xFFFFFFFF);
		graphics.text(font, "PNG artwork is loaded from your client only.", this.width / 2 - 140, 38, 0xFFAAAAAA);
		graphics.text(font, "Image path (absolute or relative to .minecraft/storyboard-displays)", this.width / 2 - 140, 48, 0xFFFFFFFF);
		graphics.text(font, "Title text (optional)", this.width / 2 - 140, 78, 0xFFFFFFFF);
		graphics.text(font, "Width / height are blocks; text size is a multiplier", this.width / 2 - 140, 112, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
