package com.zzynes.sarcophag.DRONE.item;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class StalMolotItem extends Item {
    private static final RandomSource RANDOM = RandomSource.create();

    public StalMolotItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack copy = itemStack.copy();
        copy.setCount(1);
        if (copy.hurt(1, RANDOM, null)) {
            return ItemStack.EMPTY;
        }
        return copy;
    }
}
