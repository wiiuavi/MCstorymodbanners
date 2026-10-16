package com.wiiuavi.storyboard.mixin;

import com.wiiuavi.storyboard.TransformController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public final class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void storyboard$handleUnlockedButton(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
		if (Minecraft.getInstance().screen == null
				&& TransformController.mouseUnlocked() && TransformController.active() && button.button() == 0) {
			TransformController.mouseButton(action != 0);
			callback.cancel();
		}
	}
}
