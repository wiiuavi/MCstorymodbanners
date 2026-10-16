package com.wiiuavi.storyboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StoryboardStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Display> displays = new ArrayList<>();
	private static Path file;
	private static boolean visible = true;
	private static UUID selectedId;
	private static final Set<UUID> selectedIds = new LinkedHashSet<>();

	private StoryboardStore() {}

	public static void init() {
		file = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays/displays.json");
		load();
	}

	public static List<Display> displays() { return displays; }
	public static Display selected() {
		return displays.stream().filter(display -> display.id.equals(selectedId)).findFirst()
				.orElseGet(() -> displays.isEmpty() ? null : displays.getLast());
	}
	public static void select(Display display) {
		selectedId = display == null ? null : display.id;
		selectedIds.clear();
		if (display != null) selectedIds.add(display.id);
	}
	public static boolean isSelected(Display display) { return selectedIds.contains(display.id); }
	public static void toggleSelected(Display display) {
		if (selectedIds.contains(display.id)) selectedIds.remove(display.id);
		else selectedIds.add(display.id);
		selectedId = display.id;
	}
	public static void selectAll() {
		selectedIds.clear();
		displays.stream().filter(display -> !display.hidden).forEach(display -> selectedIds.add(display.id));
		if (!selectedIds.isEmpty()) selectedId = selectedIds.iterator().next();
	}
	public static List<Display> selectedDisplays() {
		return displays.stream().filter(display -> selectedIds.contains(display.id)).toList();
	}

	public static Display displayAtCursor() {
		Minecraft client = Minecraft.getInstance();
		Display closest = null;
		double closestDistance = 0.35;
		for (Display display : displays) {
			if (display.hidden || !display.dimension.equals(client.level.dimension().identifier().toString())) continue;
			Vec3 hit = TransformController.cursorOnDisplay(client, display);
			if (hit != null && client.gameRenderer.getMainCamera().position().distanceTo(hit) < closestDistance + 128) {
				double distance = client.gameRenderer.getMainCamera().position().distanceTo(hit);
				closest = display;
				closestDistance = distance;
			}
		}
		return closest;
	}
	public static Display displayAtCrosshair() {
		Minecraft client = Minecraft.getInstance();
		if (client.hitResult == null) return null;
		Vec3 hit = client.hitResult.getLocation();
		return displays.stream()
				.filter(display -> !display.hidden && display.dimension.equals(client.level.dimension().identifier().toString()))
				.min((a, b) -> Double.compare(a.position().distanceTo(hit), b.position().distanceTo(hit)))
				.filter(display -> display.position().distanceTo(hit) < 0.35)
				.orElse(null);
	}
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
		display.name = "Display " + (displays.size() + 1);
		displays.add(display);
		select(display);
		save();
		return display;
	}

	public static void remove(Display display) {
		displays.remove(display);
		selectedIds.remove(display.id);
		save();
	}

	public static void removeSelected() {
		displays.removeIf(display -> selectedIds.contains(display.id));
		selectedIds.clear();
		selectedId = null;
		save();
	}

	public static void clear() {
		displays.clear();
		save();
	}

	public static Display duplicate(Display source) {
		if (source == null) return null;
		Display copy = GSON.fromJson(GSON.toJson(source), Display.class);
		copy.id = UUID.randomUUID();
		copy.name = source.name + " copy";
		copy.x += 0.05;
		copy.y += 0.05;
		displays.add(copy);
		select(copy);
		save();
		return copy;
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
