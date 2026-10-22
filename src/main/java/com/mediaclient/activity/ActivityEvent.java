package com.mediaclient.activity;

import net.minecraft.core.BlockPos;

public record ActivityEvent(BlockPos pos, String label, String blockId, long expiresAt) {
    public boolean expired(long now) { return now >= expiresAt; }
}
