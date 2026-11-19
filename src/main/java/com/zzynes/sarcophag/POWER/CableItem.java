package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.POWER.client.CablePlacer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

public class CableItem extends Item {

    private final CableTier tier;

    public CableItem(CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CableTier tier() {
        return tier;
    }

    // Вся прокладка живёт на клиенте: там призрак трассы, а сервер потом проверит и поставит.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CablePlacer.click(context, tier));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sarcophag.cable.amps", tier.maxAmps));
        tooltip.add(Component.translatable("tooltip.sarcophag.cable.core", Component.translatable(coreOf(stack).key())));
    }

    public static CableCore coreOf(ItemStack stack) {
        return CableCore.byIndex(stack.hasTag() ? stack.getTag().getByte("Core") : 0);
    }
}
