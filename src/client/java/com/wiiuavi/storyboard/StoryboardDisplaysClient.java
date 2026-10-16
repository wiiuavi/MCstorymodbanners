package com.wiiuavi.storyboard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class StoryboardDisplaysClient implements ClientModInitializer {
	public static final String MOD_ID = "storyboard_displays";
	private static KeyMapping editorKey;
	private static KeyMapping toggleKey;
	private static KeyMapping transformKey;
	private static KeyMapping selectNextKey;
	private static KeyMapping deleteKey;
	private static KeyMapping moveLeftKey;
	private static KeyMapping moveRightKey;
	private static KeyMapping moveUpKey;
	private static KeyMapping moveDownKey;
	private static KeyMapping moveForwardKey;
	private static KeyMapping moveBackKey;
	private static KeyMapping scaleUpKey;
	private static KeyMapping scaleDownKey;

	@Override
	public void onInitializeClient() {
		StoryboardStore.init();
		DisplayRenderer.init();
		editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.storyboard_displays.editor", GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.storyboard_displays.toggle", GLFW.GLFW_KEY_P, KeyMapping.Category.MISC));
		transformKey = key("key.storyboard_displays.transform", GLFW.GLFW_KEY_G);
		selectNextKey = key("key.storyboard_displays.select_next", GLFW.GLFW_KEY_N);
		deleteKey = key("key.storyboard_displays.delete_selected", GLFW.GLFW_KEY_DELETE);
		moveLeftKey = key("key.storyboard_displays.move_left", GLFW.GLFW_KEY_LEFT);
		moveRightKey = key("key.storyboard_displays.move_right", GLFW.GLFW_KEY_RIGHT);
		moveUpKey = key("key.storyboard_displays.move_up", GLFW.GLFW_KEY_UP);
		moveDownKey = key("key.storyboard_displays.move_down", GLFW.GLFW_KEY_DOWN);
		moveForwardKey = key("key.storyboard_displays.move_forward", GLFW.GLFW_KEY_PAGE_UP);
		moveBackKey = key("key.storyboard_displays.move_back", GLFW.GLFW_KEY_PAGE_DOWN);
		scaleUpKey = key("key.storyboard_displays.scale_up", GLFW.GLFW_KEY_EQUAL);
		scaleDownKey = key("key.storyboard_displays.scale_down", GLFW.GLFW_KEY_MINUS);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (editorKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new EditorScreen());
			}
			while (toggleKey.consumeClick()) TransformController.toggleMouseCapture(client);
			while (transformKey.consumeClick()) TransformController.cycleMode();
			while (selectNextKey.consumeClick()) TransformController.selectNext();
			while (deleteKey.consumeClick()) TransformController.deleteSelected();
			TransformController.tick(client);
		});
	}

	private static KeyMapping key(String translationKey, int keyCode) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				translationKey, keyCode, KeyMapping.Category.MISC));
	}

	public static KeyMapping moveLeftKey() { return moveLeftKey; }
	public static KeyMapping moveRightKey() { return moveRightKey; }
	public static KeyMapping moveUpKey() { return moveUpKey; }
	public static KeyMapping moveDownKey() { return moveDownKey; }
	public static KeyMapping moveForwardKey() { return moveForwardKey; }
	public static KeyMapping moveBackKey() { return moveBackKey; }
	public static KeyMapping scaleUpKey() { return scaleUpKey; }
	public static KeyMapping scaleDownKey() { return scaleDownKey; }

	public static Minecraft client() {
		return Minecraft.getInstance();
	}
}
