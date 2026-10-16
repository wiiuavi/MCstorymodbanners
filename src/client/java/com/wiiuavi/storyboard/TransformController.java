package com.wiiuavi.storyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

public final class TransformController {
	public enum Mode { OFF, MOVE, SCALE, ROTATE }
	public enum Space { PLAYER, DISPLAY }

	private static Mode mode = Mode.OFF;
	private static double lastMouseX;
	private static double lastMouseY;
	private static boolean dragging;
	private static boolean leftPressed;
	private static boolean leftWasPressed;
	private static Handle dragPart = Handle.MOVE_DEPTH;
	private static int ticksSinceSave;
	private static boolean mouseUnlocked;
	private static Space space = Space.PLAYER;
	public enum Handle {
		NONE, MOVE_DEPTH, MOVE_VERTICAL, MOVE_HORIZONTAL, SCALE, ROTATE_X, ROTATE_Y, ROTATE_Z
	}

	private TransformController() {}

	public static boolean active() { return mode != Mode.OFF; }
	public static Mode mode() { return mode; }
	public static Space space() { return space; }
	public static boolean mouseUnlocked() { return mouseUnlocked; }
	public static Handle hoveredHandle(Minecraft client) {
		return handleAtCrosshair(client, StoryboardStore.selected());
	}
	public static void mouseButton(boolean pressed) {
		leftPressed = pressed;
		if (!pressed) dragging = false;
	}

	public static void toggleSpace() {
		space = space == Space.PLAYER ? Space.DISPLAY : Space.PLAYER;
		message("Gizmo space: " + space.name());
	}

	public static void toggleMouseCapture(Minecraft client) {
		mouseUnlocked = !mouseUnlocked;
		if (mouseUnlocked) client.mouseHandler.releaseMouse();
		else client.mouseHandler.grabMouse();
		message(mouseUnlocked ? "Mouse unlocked for gizmo editing" : "Mouse locked to camera");
	}

	public static void cycleMode() {
		mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
		dragging = false;
		if (mode != Mode.OFF && StoryboardStore.selected() == null) selectNext();
		message(mode == Mode.OFF ? "Director gizmo: OFF" : "Director gizmo: " + mode.name());
	}

	public static void toggle() {
		cycleMode();
	}

	public static void selectNext() {
		if (StoryboardStore.displays().isEmpty()) return;
		Display current = StoryboardStore.selected();
		int index = current == null ? -1 : StoryboardStore.displays().indexOf(current);
		for (int i = 1; i <= StoryboardStore.displays().size(); i++) {
			Display candidate = StoryboardStore.displays().get((index + i) % StoryboardStore.displays().size());
			if (!candidate.hidden) {
				StoryboardStore.select(candidate);
				message("Selected: " + candidate.name);
				return;
			}
		}
	}

	public static void deleteSelected() {
		if (active() && !StoryboardStore.selectedDisplays().isEmpty()) {
			StoryboardStore.removeSelected();
			message("Deleted display");
		}
	}

	public static void tick(Minecraft client) {
		if (!active() || client.player == null) {
			dragging = false;
			leftPressed = false;
			leftWasPressed = false;
			return;
		}
		if (client.screen != null) {
			dragging = false;
			leftWasPressed = false;
			return;
		}
		if (!mouseUnlocked) return;
		boolean pressed = leftPressed;
		if (pressed && !leftWasPressed) {
			Display underCursor = StoryboardStore.displayAtCursor();
			if (underCursor != null && !StoryboardStore.isSelected(underCursor)) {
				StoryboardStore.select(underCursor);
			}
			dragPart = handleAtCrosshair(client, StoryboardStore.selected());
			dragging = dragPart != Handle.NONE;
		}
		leftWasPressed = pressed;
		if (StoryboardStore.selectedDisplays().isEmpty()) return;

		double mouseX = client.mouseHandler.xpos();
		double mouseY = client.mouseHandler.ypos();
		if (pressed && dragging && lastMouseX == 0 && lastMouseY == 0) {
			lastMouseX = mouseX;
			lastMouseY = mouseY;
		}
		if (!pressed) {
			dragging = false;
			lastMouseX = 0;
			lastMouseY = 0;
			return;
		}
		if (!dragging || (mouseX == lastMouseX && mouseY == lastMouseY)) return;

		double dx = mouseX - lastMouseX;
		double dy = mouseY - lastMouseY;
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		boolean changed = switch (dragPart) {
			case MOVE_DEPTH, MOVE_VERTICAL, MOVE_HORIZONTAL -> move(client, dx, dy);
			case ROTATE_X, ROTATE_Y, ROTATE_Z -> rotate(dx, client);
			case SCALE -> scale(dx, dy);
			default -> false;
		};
		if (changed && ++ticksSinceSave >= 4) {
			ticksSinceSave = 0;
			StoryboardStore.save();
		}
	}

	public static Handle handleAtCrosshair(Minecraft client, Display display) {
		if (display == null || !active()) return Handle.NONE;
		Vec3 hit = cursorOnDisplay(client, display);
		if (hit == null) return Handle.NONE;
		double[] local = localCoordinates(display, hit);
		double localX = local[0];
		double localY = local[1];
		double width = DisplayRenderer.contentWidth(display);
		double height = DisplayRenderer.contentHeight(display);
		double edge = Math.max(0.12, Math.min(width, height) * 0.12);
		return switch (mode) {
			case MOVE -> {
				if (localX >= width * 0.5 - edge && localX <= width * 0.5 + edge
						&& localY >= height * 0.5 - edge && localY <= height * 0.5 + edge) {
					yield Handle.MOVE_DEPTH;
				}
				if (Math.abs(localX - width / 2) < edge
						&& localY > height - edge && localY < height + 0.7) {
					yield Handle.MOVE_VERTICAL;
				}
				if (localX > width - edge && localX < width + 0.7
						&& Math.abs(localY - height / 2) < edge) {
					yield Handle.MOVE_HORIZONTAL;
				}
				yield Handle.NONE;
			}
			case SCALE -> {
				boolean inside = localX >= -edge && localX <= width + edge
						&& localY >= -edge && localY <= height + edge;
				yield inside ? Handle.SCALE : Handle.NONE;
			}
			case ROTATE -> {
				if (localY > height - edge && localY < height + 0.7
						&& Math.abs(localX - width / 2) < edge) yield Handle.ROTATE_Y;
				if (localX > width - edge && localX < width + 0.7
						&& Math.abs(localY - height / 2) < edge) yield Handle.ROTATE_X;
				if (localY < edge && Math.abs(localX - width / 2) < edge) yield Handle.ROTATE_Z;
				yield Handle.NONE;
			}
			default -> Handle.NONE;
		};
	}

	public static Vec3 cursorOnDisplay(Minecraft client, Display display) {
		Vec3 origin = client.gameRenderer.getMainCamera().position();
		Vec3 point = cursorPlanePoint(client);
		Vec3 direction = point.subtract(origin).normalize();
		Direction facing = display.facing;
		Vec3 normal = switch (facing) {
			case EAST -> new Vec3(1, 0, 0);
			case WEST -> new Vec3(-1, 0, 0);
			case UP -> new Vec3(0, 1, 0);
			case DOWN -> new Vec3(0, -1, 0);
			case SOUTH -> new Vec3(0, 0, 1);
			default -> new Vec3(0, 0, -1);
		};
		double denominator = direction.dot(normal);
		if (Math.abs(denominator) < 0.0001) return null;
		double distance = display.position().subtract(origin).dot(normal) / denominator;
		if (distance <= 0 || distance > 128) return null;
		Vec3 hit = origin.add(direction.scale(distance));
		double[] local = localCoordinates(display, hit);
		double width = DisplayRenderer.contentWidth(display);
		double height = DisplayRenderer.contentHeight(display);
		double handle = Math.min(0.12, Math.max(0.05, Math.min(width, height) * 0.08));
		double margin = active() ? Math.max(2.0, handle + 0.35) : 0.25;
		return local[0] >= -margin && local[0] <= width + margin
				&& local[1] >= -margin && local[1] <= height + margin ? hit : null;
	}

	private static Vec3 cursorPlanePoint(Minecraft client) {
		double width = client.getWindow().getGuiScaledWidth();
		double height = client.getWindow().getGuiScaledHeight();
		float x = (float) (client.mouseHandler.getScaledXPos(client.getWindow()) / Math.max(1, width) * 2.0 - 1.0);
		float y = (float) (1.0 - client.mouseHandler.getScaledYPos(client.getWindow()) / Math.max(1, height) * 2.0);
		return client.gameRenderer.getMainCamera().position()
				.add(client.gameRenderer.getMainCamera().getNearPlane(1.0f).getPointOnPlane(x, y));
	}

	private static double[] localCoordinates(Display display, Vec3 hit) {
		double x = hit.x - display.x;
		double y = hit.y - display.y;
		double horizontal = switch (display.facing) {
			case EAST, WEST -> hit.z - display.z;
			default -> x;
		};
		if (display.facing == Direction.NORTH || display.facing == Direction.WEST) horizontal = -horizontal;
		return new double[] { horizontal, y };
	}

	private static boolean move(Minecraft client, double dx, double dy) {
		var camera = client.gameRenderer.getMainCamera();
		Vec3 right;
		Vec3 forward;
		if (space == Space.PLAYER) {
			float yaw = (float) Math.toRadians(client.player.getYRot());
			forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		} else {
			float yaw = (float) Math.toRadians(displayYaw());
			forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		}
		Vec3 up = new Vec3(0, 1, 0);
		double sensitivity = client.player.isCrouching() ? 0.0025 : 0.01;
		Vec3 delta = switch (dragPart) {
			case MOVE_HORIZONTAL -> right.scale(dx * sensitivity);
			case MOVE_VERTICAL -> up.scale(-dy * sensitivity);
			default -> forward.scale(-dy * sensitivity);
		};
		if (client.hasShiftDown()) {
			delta = new Vec3(snap(delta.x), snap(delta.y), snap(delta.z));
		}
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.x += delta.x;
			display.y += delta.y;
			display.z += delta.z;
		}
		return delta.lengthSqr() > 0;
	}

	private static float displayYaw() {
		Display display = StoryboardStore.selected();
		if (display == null) return 0;
		return switch (display.facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
	}

	private static boolean scale(double dx, double dy) {
		float amount = (float) ((dx - dy) * 0.01);
		if (Math.abs(amount) < 0.001f) return false;
		for (Display display : StoryboardStore.selectedDisplays()) {
			float originalWidth = display.width;
			display.width = Math.max(0.05f, display.width + amount);
			if (Minecraft.getInstance().hasControlDown()) {
				display.height = Math.max(0.05f, display.height - (float) (dy * 0.01));
			} else {
				float ratio = display.height / Math.max(0.05f, originalWidth);
				display.height = Math.max(0.05f, display.width * ratio);
			}
			if (Minecraft.getInstance().hasShiftDown()) {
				display.width = snap(display.width);
				display.height = snap(display.height);
			}
		}
		return true;
	}

	private static boolean rotate(double dx, Minecraft client) {
		float step = (float) (dx * 0.5);
		if (Math.abs(step) < 0.1f) return false;
		for (Display display : StoryboardStore.selectedDisplays()) {
			switch (dragPart) {
				case ROTATE_X -> display.rotationX += step;
				case ROTATE_Y -> display.rotationY += step;
				default -> display.rotationZ += step;
			}
			if (client.hasShiftDown()) {
				display.rotationX = Math.round(display.rotationX / 45.0f) * 45.0f;
				display.rotationY = Math.round(display.rotationY / 45.0f) * 45.0f;
				display.rotationZ = Math.round(display.rotationZ / 45.0f) * 45.0f;
			}
		}
		return true;
	}

	private static double snap(double value) {
		return Math.round(value * 2.0) / 2.0;
	}

	private static float snap(float value) {
		return Math.max(0.05f, Math.round(value * 2.0f) / 2.0f);
	}

	private static void message(String value) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) client.player.sendOverlayMessage(Component.literal(value));
	}
}
