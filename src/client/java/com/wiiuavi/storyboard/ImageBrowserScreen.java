package com.wiiuavi.storyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public final class ImageBrowserScreen extends Screen {
	private final EditorScreen parent;
	private final List<String> images;
	private final List<net.minecraft.resources.Identifier> thumbnails;
	private final List<int[]> dimensions;

	public ImageBrowserScreen(EditorScreen parent) {
		super(Component.literal("Storyboard PNGs"));
		this.parent = parent;
		Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays");
		List<String> found;
		try (var paths = Files.list(folder)) {
			found = paths.filter(path -> Files.isRegularFile(path)
					&& path.getFileName().toString().toLowerCase().endsWith(".png"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
					.map(path -> path.toAbsolutePath().toString()).toList();
		} catch (IOException exception) {
			StoryboardDisplays.LOGGER.warn("Could not list storyboard PNGs in {}", folder, exception);
			found = List.of();
		}
		images = found;
		thumbnails = images.stream().map(DisplayRenderer::previewTexture).toList();
		dimensions = new ArrayList<>();
		for (String image : images) {
			try (var nativeImage = com.mojang.blaze3d.platform.NativeImage.read(
					Files.newInputStream(Path.of(image)))) {
				dimensions.add(new int[] {nativeImage.getWidth(), nativeImage.getHeight()});
			} catch (IOException | RuntimeException exception) {
				StoryboardDisplays.LOGGER.warn("Could not read storyboard PNG dimensions for {}", image, exception);
				dimensions.add(new int[] {1, 1});
			}
		}
	}

	@Override
	protected void init() {
		int y = 45;
		for (String image : images) {
			String name = Path.of(image).getFileName().toString();
			addRenderableWidget(Button.builder(Component.literal(name), button -> parent.chooseImage(image))
					.bounds(30, y, 360, 42).build());
			y += 46;
			if (y > height - 45) break;
		}
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> minecraft.setScreen(parent))
				.bounds(30, height - 32, 100, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Open folder"), button -> openFolder())
				.bounds(140, height - 32, 120, 22).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(15, 15, width - 15, height - 15, 0xDD10151E);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 22, 0xFFFFFFFF);
		int y = 45;
		for (int i = 0; i < images.size() && y <= height - 45; i++, y += 46) {
			var thumbnail = thumbnails.get(i);
			int[] size = dimensions.get(i);
			if (thumbnail != null) graphics.blit(thumbnail, 400, y + 3, 36, 36,
					0.0f, 0.0f, 1.0f, 1.0f);
		}
		if (images.isEmpty()) {
			graphics.centeredText(font, Component.literal("No PNG files found in .minecraft/storyboard-displays"),
					width / 2, 55, 0xFFFFCC66);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void openFolder() {
		Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays");
		try {
			Files.createDirectories(folder);
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(folder.toFile());
			} else {
				StoryboardDisplays.LOGGER.warn("Desktop folder opening is unavailable for {}", folder);
			}
		} catch (IOException | RuntimeException exception) {
			StoryboardDisplays.LOGGER.warn("Could not open storyboard folder {}", folder, exception);
		}
	}
}
