package com.wiiuavi.storyboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class Display {
	public UUID id = UUID.randomUUID();
	public String name = "Untitled display";
	public boolean hidden;
	public String dimension = "";
	public double x, y, z;
	public Direction facing = Direction.NORTH;
	public float rotation;
	public float rotationX;
	public float rotationY;
	public float rotationZ;
	public float width = 4.0f;
	public float height = 2.25f;
	public String imagePath = "";
	public String text = "";
	public int color = 0xFFFFFFFF;
	public float textScale = 1.0f;
	public boolean emissive;

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	public BlockPos blockPos() {
		return BlockPos.containing(x, y, z);
	}
}
