package com.mediaclient.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public record SchematicData(int width, int height, int length, List<Placement> blocks) {
    public record Placement(BlockPos relativePos, BlockState requiredState) { }
}
