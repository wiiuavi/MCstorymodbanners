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

	@Override
	public void onInitializeClient() {
		StoryboardStore.init();
		DisplayRenderer.init();
		editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.storyboard_displays.editor", GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.storyboard_displays.toggle", GLFW.GLFW_KEY_P, KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (editorKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new EditorScreen());
			}
			while (toggleKey.consumeClick()) StoryboardStore.setVisible(!StoryboardStore.isVisible());
		});
	}

	public static Minecraft client() {
		return Minecraft.getInstance();
	}
}
