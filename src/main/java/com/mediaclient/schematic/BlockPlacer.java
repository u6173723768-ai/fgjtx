package com.mediaclient.schematic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Placement helper that uses normal client interactions and validates reach/support. */
public final class BlockPlacer {
    private BlockPlacer() { }

    public static boolean canPlace(Minecraft client, BlockPos target) {
        return findHit(client, target) != null;
    }

    public static boolean place(Minecraft client, BlockPos target) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) return false;
        BlockHitResult hit = findHit(client, target);
        if (hit == null) return false;
        InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        return result.consumesAction();
    }

    private static BlockHitResult findHit(Minecraft client, BlockPos target) {
        LocalPlayer player = client.player;
        Level level = client.level;
        if (player == null || level == null || client.gameMode == null) return null;
        BlockState targetState = level.getBlockState(target);
        if (!targetState.isAir() && !targetState.canBeReplaced()) return null;
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            BlockPos supportPos = target.relative(direction);
            if (!level.hasChunkAt(supportPos)) continue;
            BlockState support = level.getBlockState(supportPos);
            if (support.isAir() || support.canBeReplaced() || support.getCollisionShape(level, supportPos).isEmpty()) continue;
            Direction face = direction.getOpposite();
            Vec3 hitLocation = Vec3.atCenterOf(supportPos).add(face.getStepX() * 0.499, face.getStepY() * 0.499, face.getStepZ() * 0.499);
            if (player.getEyePosition().distanceToSqr(hitLocation) > 4.5 * 4.5) continue;
            return new BlockHitResult(hitLocation, face, supportPos, false);
        }
        return null;
    }
}
