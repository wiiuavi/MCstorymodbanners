package com.wiiuavi.storyboard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DisplayRenderer {
	private static final Map<String, Identifier> textures = new HashMap<>();
	private static final Map<String, Boolean> failedTextures = new HashMap<>();
	private static final Map<String, Float> aspectRatios = new HashMap<>();

	private DisplayRenderer() {}

	public static void init() {
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(DisplayRenderer::renderDisplays);
		LevelRenderEvents.BEFORE_GIZMOS.register(DisplayRenderer::renderGizmos);
	}

	public static void logImageDiagnostics(String path) {
		if (path == null || path.isBlank()) {
			StoryboardDisplays.LOGGER.info("Storyboard image diagnostics: no path supplied");
			return;
		}
		Path file = Path.of(path);
		if (!file.isAbsolute()) {
			file = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays").resolve(path);
		}
		try (NativeImage image = NativeImage.read(Files.newInputStream(file))) {
			int sample = image.getPixel(0, 0);
			StoryboardDisplays.LOGGER.info(
					"Storyboard image diagnostics: file={}, format={}, size={}x{}, firstPixel=0x{}",
					file, image.format(), image.getWidth(), image.getHeight(),
					Integer.toHexString(sample));
		} catch (IOException | RuntimeException exception) {
			StoryboardDisplays.LOGGER.warn("Storyboard image diagnostics failed for {}", file, exception);
		}
	}

	private static void renderDisplays(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!StoryboardStore.isVisible() || client.level == null || client.player == null) return;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack pose = context.poseStack();
		MultiBufferSource buffers = context.bufferSource();

		for (Display display : StoryboardStore.displays()) {
			if (display.hidden) continue;
			if (!display.dimension.equals(client.level.dimension().identifier().toString())) continue;
			if (display.position().distanceToSqr(camera) > 4096.0) continue;
			pose.pushPose();
			pose.translate(display.x - camera.x, display.y - camera.y, display.z - camera.z);
			orient(pose, display.facing);
			pose.mulPose(new Quaternionf()
					.rotateX((float) Math.toRadians(display.rotationX))
					.rotateY((float) Math.toRadians(display.rotationY))
					.rotateZ((float) Math.toRadians(display.rotationZ + display.rotation)));
			renderCard(display, pose, buffers);
			pose.popPose();
		}
	}

	private static void renderGizmos(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen != null || !TransformController.active() || !StoryboardStore.isVisible()
				|| client.level == null || client.player == null) return;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack pose = context.poseStack();
		MultiBufferSource buffers = context.bufferSource();
		for (Display display : StoryboardStore.selectedDisplays()) {
			if (display.hidden || !display.dimension.equals(client.level.dimension().identifier().toString())) continue;
			if (display.position().distanceToSqr(camera) > 4096.0) continue;
			pose.pushPose();
			pose.translate(display.x - camera.x, display.y - camera.y, display.z - camera.z);
			orient(pose, display.facing);
			pose.mulPose(new Quaternionf()
					.rotateX((float) Math.toRadians(display.rotationX))
					.rotateY((float) Math.toRadians(display.rotationY))
					.rotateZ((float) Math.toRadians(display.rotationZ + display.rotation)));
			renderGizmo(display, pose, buffers, TransformController.handleAtCrosshair(client, display));
			pose.popPose();
		}
	}

	private static void renderGhostOutline(Display display, PoseStack pose, MultiBufferSource buffers) {
		var matrix = pose.last().pose();
		var border = buffers.getBuffer(RenderTypes.debugQuads());
		float thickness = 0.035f;
		int color = 0x9955D9FF;
		borderQuad(border, matrix, 0, 0, display.width, thickness, color);
		borderQuad(border, matrix, 0, display.height - thickness, display.width, display.height, color);
		borderQuad(border, matrix, 0, 0, thickness, display.height, color);
		borderQuad(border, matrix, display.width - thickness, 0, display.width, display.height, color);
	}

	private static void renderGizmo(Display display, PoseStack pose, MultiBufferSource buffers,
			TransformController.Handle hovered) {
		var matrix = pose.last().pose();
		var border = buffers.getBuffer(RenderTypes.debugQuads());
		float handle = Math.min(0.12f, Math.max(0.05f, Math.min(display.width, display.height) * 0.08f));
		int normal = 0xFF4DB6FF;
		int hot = 0xFFFFE066;
		float frameWidth = contentWidth(display);
		float frameHeight = contentHeight(display);
		borderQuad(border, matrix, 0, 0, frameWidth, 0.025f, 0xFF55D9FF);
		borderQuad(border, matrix, 0, frameHeight - 0.025f, frameWidth, frameHeight, 0xFF55D9FF);
		borderQuad(border, matrix, 0, 0, 0.025f, frameHeight, 0xFF55D9FF);
		borderQuad(border, matrix, frameWidth - 0.025f, 0, frameWidth, frameHeight, 0xFF55D9FF);
		switch (TransformController.mode()) {
			case MOVE -> {
				gizmoBox(border, matrix, frameWidth / 2, frameHeight / 2, handle, normal,
						hovered == TransformController.Handle.MOVE_DEPTH ? hot : normal);
				gizmoBox(border, matrix, frameWidth / 2, frameHeight + 0.3f, handle, normal,
						hovered == TransformController.Handle.MOVE_VERTICAL ? hot : normal);
				gizmoBox(border, matrix, frameWidth + 0.3f, frameHeight / 2, handle, normal,
						hovered == TransformController.Handle.MOVE_HORIZONTAL ? hot : normal);
			}
			case SCALE -> {
				gizmoBox(border, matrix, 0, 0, handle, normal, hovered == TransformController.Handle.SCALE ? hot : normal);
				gizmoBox(border, matrix, frameWidth, 0, handle, normal, hovered == TransformController.Handle.SCALE ? hot : normal);
				gizmoBox(border, matrix, 0, frameHeight, handle, normal, hovered == TransformController.Handle.SCALE ? hot : normal);
				gizmoBox(border, matrix, frameWidth, frameHeight, handle, normal, hovered == TransformController.Handle.SCALE ? hot : normal);
				gizmoBox(border, matrix, frameWidth / 2, frameHeight / 2, handle, normal,
						hovered == TransformController.Handle.SCALE ? hot : normal);
			}
			case ROTATE -> {
				gizmoBox(border, matrix, frameWidth / 2, frameHeight + 0.3f, handle, 0xFFFF5C5C,
						hovered == TransformController.Handle.ROTATE_Y ? hot : 0xFFFF5C5C);
				gizmoBox(border, matrix, frameWidth + 0.3f, frameHeight / 2, handle, 0xFF72E06A,
						hovered == TransformController.Handle.ROTATE_X ? hot : 0xFF72E06A);
				gizmoBox(border, matrix, frameWidth / 2, -0.3f, handle, 0xFFB77CFF,
						hovered == TransformController.Handle.ROTATE_Z ? hot : 0xFFB77CFF);
			}
			default -> {}
		}
	}

	public static float contentWidth(Display display) {
		float textWidth = display.text == null || display.text.isBlank()
				? 0 : Minecraft.getInstance().font.width(Component.literal(display.text))
				* display.textScale / 16.0f;
		return Math.max(display.width, textWidth);
	}

	public static float contentHeight(Display display) {
		float textHeight = display.text == null || display.text.isBlank()
				? 0 : 10.0f * display.textScale / 16.0f;
		return Math.max(display.height, textHeight);
	}

	private static void gizmoBox(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
			float centerX, float centerY, float size, int normal, int color) {
		borderQuad(builder, matrix, centerX - size, centerY - size, centerX + size, centerY + size, color);
	}

	private static void borderQuad(com.mojang.blaze3d.vertex.VertexConsumer builder,
			org.joml.Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
		vertex(builder, matrix, x1, y1, 0.002f, 0, 0).setColor(color);
		vertex(builder, matrix, x2, y1, 0.002f, 1, 0).setColor(color);
		vertex(builder, matrix, x2, y2, 0.002f, 1, 1).setColor(color);
		vertex(builder, matrix, x1, y2, 0.002f, 0, 1).setColor(color);
	}

	private static void renderCard(Display display, PoseStack pose, MultiBufferSource buffers) {
		var matrix = pose.last().pose();
		Identifier texture = texture(display.imagePath);
		if (texture != null) {
			// Artwork is presentation graphics rather than a world block: keep it full-bright
			// so a floor or ceiling placement does not become unreadably dark.
			// Cutout rendering keeps transparent PNG pixels transparent while writing
			// depth for opaque pixels.
			RenderType renderType = RenderTypes.entityCutout(texture, false);
			var builder = buffers.getBuffer(renderType);
			vertex(builder, matrix, 0, 0, 0, 0, 1);
			vertex(builder, matrix, display.width, 0, 0, 1, 1);
			vertex(builder, matrix, display.width, display.height, 0, 1, 0);
			vertex(builder, matrix, 0, display.height, 0, 0, 0);
		} else if (display.imagePath != null && !display.imagePath.isBlank()) {
			var builder = buffers.getBuffer(RenderTypes.debugQuads());
			int color = display.color;
			vertex(builder, matrix, 0, 0, 0, 0, 1).setColor(color);
			vertex(builder, matrix, display.width, 0, 0, 1, 1).setColor(color);
			vertex(builder, matrix, display.width, display.height, 0, 1, 0).setColor(color);
			vertex(builder, matrix, 0, display.height, 0, 0, 0).setColor(color);
		}
		if (!display.text.isBlank()) {
			pose.pushPose();
			// Font quads use the opposite front-face convention to the display quad.
			// Flip the local text plane so it is readable from the display's front.
			if (display.facing.getAxis().isVertical()) {
				pose.mulPose(new Quaternionf().rotateY((float) Math.PI));
			} else {
				pose.mulPose(new Quaternionf().rotateX((float) Math.PI));
			}
			pose.scale(display.textScale, display.textScale, display.textScale);
			Minecraft.getInstance().font.drawInBatch(Component.literal(display.text), 0, 0,
					display.color, display.emissive, pose.last().pose(), buffers,
					net.minecraft.client.gui.Font.DisplayMode.NORMAL, 15728880, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
	}

	private static void orient(PoseStack pose, Direction facing) {
		float angle = switch (facing) {
			case NORTH -> 180.0f;
			case WEST -> -90.0f;
			case EAST -> 90.0f;
			default -> 0.0f;
		};
		if (facing.getAxis().isVertical()) {
			pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(facing == Direction.UP ? -90.0f : 90.0f)));
		} else {
			pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(angle)));
		}
	}

	private static com.mojang.blaze3d.vertex.VertexConsumer vertex(
			com.mojang.blaze3d.vertex.VertexConsumer builder,
			org.joml.Matrix4f matrix, float x, float y, float z, float u, float v) {
		return builder.addVertex(matrix, x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setUv1(OverlayTexture.NO_WHITE_U, OverlayTexture.WHITE_OVERLAY_V)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(0xF000F0)
				.setNormal(0, 0, 1);
	}

	private static Identifier texture(String path) {
		if (path == null || path.isBlank()) return null;
		Identifier cached = textures.get(path);
		if (cached != null) return cached;
		if (failedTextures.containsKey(path)) return null;
		Path file = Path.of(path);
		if (!file.isAbsolute()) {
			file = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays").resolve(path);
		}

		try {
			NativeImage image = NativeImage.read(Files.newInputStream(file));
			Identifier id = Identifier.fromNamespaceAndPath(StoryboardDisplays.MOD_ID,
					"local/" + Integer.toUnsignedString(path.hashCode()));
			DynamicTexture texture = new DynamicTexture(() -> "Storyboard display " + path, image);
			texture.upload();
			Minecraft.getInstance().getTextureManager().register(id, texture);
			textures.put(path, id);
			return id;
		} catch (IOException | RuntimeException exception) {
			failedTextures.put(path, true);
			StoryboardDisplays.LOGGER.warn("Could not load storyboard PNG {}", file, exception);
			return null;
		}
	}

	public static float imageAspectRatio(String path) {
		if (path == null || path.isBlank()) return 16.0f / 9.0f;
		Float cached = aspectRatios.get(path);
		if (cached != null) return cached;
		Path file = Path.of(path);
		if (!file.isAbsolute()) file = Minecraft.getInstance().gameDirectory.toPath().resolve("storyboard-displays").resolve(path);
		try (NativeImage image = NativeImage.read(Files.newInputStream(file))) {
			float ratio = image.getWidth() / (float) image.getHeight();
			aspectRatios.put(path, ratio);
			return ratio;
		} catch (IOException | RuntimeException exception) {
			return 16.0f / 9.0f;
		}
	}
}
