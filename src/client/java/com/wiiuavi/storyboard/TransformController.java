package com.wiiuavi.storyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
		NONE, MOVE_DEPTH, MOVE_VERTICAL, MOVE_HORIZONTAL, SCALE,
		SCALE_TOP_LEFT, SCALE_TOP_RIGHT, SCALE_BOTTOM_LEFT, SCALE_BOTTOM_RIGHT,
		ROTATE_X, ROTATE_Y, ROTATE_Z
	}

	private TransformController() {}

	public static boolean active() { return mode != Mode.OFF; }
	public static Mode mode() { return mode; }
	public static Space space() { return space; }
	public static boolean mouseUnlocked() { return mouseUnlocked; }
	public static Handle hoveredHandle(Minecraft client) {
		return handleAtCursor(client, StoryboardStore.selected());
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
		client.options.keyShift.setDown(false);
		if (!mouseUnlocked) return;
		boolean pressed = leftPressed;
		if (pressed && !leftWasPressed) {
			Display underCursor = StoryboardStore.displayAtCursor();
			if (underCursor != null && !StoryboardStore.isSelected(underCursor)) {
				StoryboardStore.select(underCursor);
			}
			dragPart = handleAtCursor(client, StoryboardStore.selected());
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
			case SCALE, SCALE_TOP_LEFT, SCALE_TOP_RIGHT, SCALE_BOTTOM_LEFT, SCALE_BOTTOM_RIGHT -> scale(dx, dy);
			default -> false;
		};
		if (changed && ++ticksSinceSave >= 4) {
			ticksSinceSave = 0;
			StoryboardStore.save();
		}
	}

	public static Handle handleAtCrosshair(Minecraft client, Display display) {
		return handleAtCursor(client, display);
	}

	public static Handle handleAtCursor(Minecraft client, Display display) {
		if (display == null || !active()) return Handle.NONE;
		double width = DisplayRenderer.contentWidth(display);
		double height = DisplayRenderer.contentHeight(display);
		double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
		double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
		double screenWidth = client.getWindow().getGuiScaledWidth();
		double screenHeight = client.getWindow().getGuiScaledHeight();
		Handle[] handles = handlesFor(width, height);
		double best = 18.0 * 18.0;
		Handle result = Handle.NONE;
		for (Handle handle : handles) {
			Vec3 screen = client.gameRenderer.projectPointToScreen(
					localToWorld(display, handleX(handle, width, height), handleY(handle, width, height)));
			double projectedX = (screen.x + 1.0) * screenWidth * 0.5;
			double projectedY = (1.0 - screen.y) * screenHeight * 0.5;
			double dx = projectedX - mouseX;
			double dy = projectedY - mouseY;
			double distance = dx * dx + dy * dy;
			if (screen.z >= -1 && screen.z <= 1 && distance < best) {
				best = distance;
				result = handle;
			}
		}
		return result;
	}

	private static Handle[] handlesFor(double width, double height) {
		return switch (mode) {
			case MOVE -> new Handle[] {Handle.MOVE_DEPTH, Handle.MOVE_VERTICAL, Handle.MOVE_HORIZONTAL};
			case SCALE -> new Handle[] {Handle.SCALE_TOP_LEFT, Handle.SCALE_TOP_RIGHT,
					Handle.SCALE_BOTTOM_LEFT, Handle.SCALE_BOTTOM_RIGHT, Handle.SCALE};
			case ROTATE -> new Handle[] {Handle.ROTATE_Z, Handle.ROTATE_Y, Handle.ROTATE_X};
			default -> new Handle[0];
		};
	}

	private static double handleX(Handle handle, double width, double height) {
		return switch (handle) {
			case MOVE_VERTICAL, ROTATE_Y -> width * 0.5;
			case MOVE_HORIZONTAL, ROTATE_X -> width + 0.3;
			case ROTATE_Z -> -0.3;
			case SCALE_TOP_LEFT, SCALE_BOTTOM_LEFT -> 0;
			case SCALE_TOP_RIGHT, SCALE_BOTTOM_RIGHT -> width;
			default -> width * 0.5;
		};
	}

	private static double handleY(Handle handle, double width, double height) {
		return switch (handle) {
			case MOVE_DEPTH, SCALE -> height * 0.5;
			case MOVE_VERTICAL, ROTATE_Y -> height + 0.3;
			case MOVE_HORIZONTAL, ROTATE_X -> height * 0.5;
			case ROTATE_Z -> height + 0.3;
			case SCALE_TOP_LEFT, SCALE_TOP_RIGHT -> 0;
			case SCALE_BOTTOM_LEFT, SCALE_BOTTOM_RIGHT -> height;
			default -> height * 0.5;
		};
	}

	private static Vec3 localToWorld(Display display, double localX, double localY) {
		double width = display.width;
		double height = display.height;
		Vector3f point = new Vector3f((float) (localX - width * 0.5), (float) (localY - height * 0.5), 0);
		point.rotate(new Quaternionf()
				.rotateX((float) Math.toRadians(display.rotationX))
				.rotateY((float) Math.toRadians(display.rotationY))
				.rotateZ((float) Math.toRadians(display.rotationZ + display.rotation)));
		point.add((float) width * 0.5f, (float) height * 0.5f, 0);
		Quaternionf facing = switch (display.facing) {
			case NORTH -> new Quaternionf().rotateY((float) Math.PI);
			case WEST -> new Quaternionf().rotateY((float) -Math.PI / 2);
			case EAST -> new Quaternionf().rotateY((float) Math.PI / 2);
			case UP -> new Quaternionf().rotateX((float) -Math.PI / 2);
			case DOWN -> new Quaternionf().rotateX((float) Math.PI / 2);
			default -> new Quaternionf();
		};
		point.rotate(facing);
		return new Vec3(display.x + point.x, display.y + point.y, display.z + point.z);
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
		if (space == Space.PLAYER) {
			forward = cardinalize(forward);
			right = new Vec3(-forward.z, 0, forward.x);
		}
		Vec3 up = new Vec3(0, 1, 0);
		double sensitivity = client.player.isCrouching() ? 0.0025 : 0.01;
		Vec3 delta = switch (dragPart) {
			case MOVE_HORIZONTAL -> right.scale(dx * sensitivity);
			case MOVE_VERTICAL -> up.scale(-dy * sensitivity);
			default -> forward.scale(-dy * sensitivity);
		};
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.x += delta.x;
			display.y += delta.y;
			display.z += delta.z;
		}
		return delta.lengthSqr() > 0;
	}

	private static Vec3 cardinalize(Vec3 vector) {
		return Math.abs(vector.x) >= Math.abs(vector.z)
				? new Vec3(Math.copySign(1, vector.x), 0, 0)
				: new Vec3(0, 0, Math.copySign(1, vector.z));
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
			if (display.text != null && !display.text.isBlank()
				&& (display.imagePath == null || display.imagePath.isBlank())) {
			display.textScale = Math.max(0.05f, display.textScale + amount);
			continue;
			}
			float originalWidth = display.width;
			display.width = Math.max(0.05f, display.width + amount);
			if (Minecraft.getInstance().hasControlDown()) {
				display.height = Math.max(0.05f, display.height - (float) (dy * 0.01));
			} else {
				float ratio = display.height / Math.max(0.05f, originalWidth);
				display.height = Math.max(0.05f, display.width * ratio);
			}

		}
		return true;
	}

	public static void resetMove() {
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.x = display.resetX;
			display.y = display.resetY;
			display.z = display.resetZ;
		}
		StoryboardStore.save();
	}

	public static void resetScale() {
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.width = display.resetWidth;
			display.height = display.resetHeight;
		}
		StoryboardStore.save();
	}

	public static void resetRotation() {
		for (Display display : StoryboardStore.selectedDisplays()) {
			display.rotation = 0;
			display.rotationX = 0;
			display.rotationY = 0;
			display.rotationZ = 0;
		}
		StoryboardStore.save();
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
		}
		return true;
	}

	private static void message(String value) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) client.player.sendOverlayMessage(Component.literal(value));
	}
}
