package com.angelofdeath.mod.item;

import com.angelofdeath.mod.logic.SingularityActions;
import com.angelofdeath.mod.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class AngelOfDeathWeaponItem extends Item {
    public static final int MODE_BLACK_HOLE = 0;
    public static final int MODE_WHITE_HOLE = 1;
    public static final int MODE_GREY_HOLE = 2;
    public static final int MAX_POWER = 5;
    public static final int FUSION_TICKS = 30;
    public static final double FOCAL_DISTANCE = 2.5D;

    public AngelOfDeathWeaponItem(Properties properties) {
        super(properties);
    }

    public static int getMode(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.SINGULARITY_MODE.get(), 0), 0, 2);
    }

    public static int getPower(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.POWER_LEVEL.get(), 1), 1, MAX_POWER);
    }

    public static MutableComponent modeMessage(int mode) {
        return switch (mode) {
            case MODE_WHITE_HOLE -> Component.translatable("message.angelofdeath.mode.white_hole").withStyle(ChatFormatting.WHITE);
            case MODE_GREY_HOLE -> Component.translatable("message.angelofdeath.mode.grey_hole").withStyle(ChatFormatting.GRAY);
            default -> Component.translatable("message.angelofdeath.mode.black_hole").withStyle(ChatFormatting.DARK_GRAY);
        };
    }

    private static MutableComponent modeName(int mode) {
        return switch (mode) {
            case MODE_WHITE_HOLE -> Component.translatable("tooltip.angelofdeath.mode.white_hole").withStyle(ChatFormatting.WHITE);
            case MODE_GREY_HOLE -> Component.translatable("tooltip.angelofdeath.mode.grey_hole").withStyle(ChatFormatting.GRAY);
            default -> Component.translatable("tooltip.angelofdeath.mode.black_hole").withStyle(ChatFormatting.DARK_GRAY);
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                cyclePower(level, player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void cyclePower(Level level, Player player, ItemStack stack) {
        int current = getPower(stack);
        int next = current >= MAX_POWER ? 1 : current + 1;
        stack.set(ModDataComponents.POWER_LEVEL.get(), next);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.5F + 0.25F * next);
        player.displayClientMessage(Component.translatable("message.angelofdeath.power", next)
                .withStyle(ChatFormatting.GOLD), true);
        player.getCooldowns().addCooldown(this, 10);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        int heldTicks = getUseDuration(stack) - timeLeft;
        if (getMode(stack) == MODE_GREY_HOLE && heldTicks >= FUSION_TICKS) {
            SingularityActions.spawnFromPlayer(player, stack);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.angelofdeath.mode", modeName(getMode(stack))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.angelofdeath.power", getPower(stack)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.angelofdeath.hint.switch").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.angelofdeath.hint.power").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.angelofdeath.hint.channel").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.angelofdeath.hint.place").withStyle(ChatFormatting.DARK_GRAY));
    }
}
