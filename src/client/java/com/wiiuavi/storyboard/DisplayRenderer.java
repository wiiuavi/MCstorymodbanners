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
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DisplayRenderer {
	private static final Map<String, Identifier> textures = new HashMap<>();
	private static final Map<String, Boolean> failedTextures = new HashMap<>();

	private DisplayRenderer() {}

	public static void init() {
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(DisplayRenderer::render);
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

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!StoryboardStore.isVisible() || client.level == null || client.player == null) return;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack pose = context.poseStack();
		MultiBufferSource buffers = context.bufferSource();

		for (Display display : StoryboardStore.displays()) {
			if (!display.dimension.equals(client.level.dimension().identifier().toString())) continue;
			if (display.position().distanceToSqr(camera) > 4096.0) continue;
			pose.pushPose();
			pose.translate(display.x - camera.x, display.y - camera.y, display.z - camera.z);
			orient(pose, display.facing);
			renderCard(display, pose, buffers);
			pose.popPose();
		}
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
}
