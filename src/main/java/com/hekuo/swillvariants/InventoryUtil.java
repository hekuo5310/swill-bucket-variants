package com.hekuo.swillvariants;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;

final class InventoryUtil {
    private InventoryUtil() {}
    static boolean canPlayerUse(BlockEntity blockEntity, PlayerEntity player) {
        return blockEntity.getWorld() != null
                && blockEntity.getWorld().getBlockEntity(blockEntity.getPos()) == blockEntity
                && player.squaredDistanceTo(blockEntity.getPos().getX() + .5, blockEntity.getPos().getY() + .5,
                blockEntity.getPos().getZ() + .5) <= 64.0;
    }
}
