package com.wiiuavi.storyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class StoryboardStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Display> displays = new ArrayList<>();
	private static Path file;
	private static boolean visible = true;

	private StoryboardStore() {}

	public static void init() {
		file = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays/displays.json");
		load();
	}

	public static List<Display> displays() { return displays; }
	public static boolean isVisible() { return visible; }
	public static void setVisible(boolean value) { visible = value; }

	public static Display createAtCrosshair() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.hitResult == null) return null;
		if (client.hitResult.getType() != HitResult.Type.BLOCK) return null;
		BlockHitResult hit = (BlockHitResult) client.hitResult;
		Display display = new Display();
		display.x = hit.getLocation().x;
		display.y = hit.getLocation().y;
		display.z = hit.getLocation().z;
		display.facing = hit.getDirection();
		display.dimension = client.level.dimension().identifier().toString();
		displays.add(display);
		save();
		return display;
	}

	public static void remove(Display display) {
		displays.remove(display);
		save();
	}

	public static void clear() {
		displays.clear();
		save();
	}

	public static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(displays));
		} catch (IOException exception) {
			StoryboardDisplays.LOGGER.error("Could not save displays to {}", file, exception);
		}
	}

	private static void load() {
		if (file == null || !Files.exists(file)) return;
		try {
			Display[] saved = GSON.fromJson(Files.readString(file), Display[].class);
			if (saved != null) displays.addAll(List.of(saved));
		} catch (Exception exception) {
			StoryboardDisplays.LOGGER.error("Could not load displays from {}", file, exception);
		}
	}
}
