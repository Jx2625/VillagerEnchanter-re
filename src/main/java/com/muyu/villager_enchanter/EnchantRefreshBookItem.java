package com.muyu.villager_enchanter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EnchantRefreshBookItem extends Item {

    // NBT 键：存储在村民 PersistentData 中
    private static final String REFRESH_INDEX_TAG = "VE_RefreshIndex";

    public EnchantRefreshBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (!(entity instanceof Villager villager)) {
            return super.interactLivingEntity(stack, user, entity, hand);
        }

        Level world = user.level();

        // 客户端直接返回成功（不执行逻辑）
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // ===== 服务端执行刷新逻辑 =====
        // 从缓存获取附魔列表
        List<Holder<Enchantment>> holders = VillagerEnchanter.getEnchantmentHolders();
        if (holders.isEmpty()) {
            VillagerEnchanter.LOGGER.warn("Enchantment cache is empty!");
            user.displayClientMessage(
                    Component.translatable("message.villager_enchanter.no_enchantments")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        MerchantOffers offers = villager.getOffers();

        // === 收集所有附魔书交易的索引（只看结果物品）===
        List<Integer> enchantIndices = new ArrayList<>();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getResult().getItem() == Items.ENCHANTED_BOOK) {
                enchantIndices.add(i);
            }
        }

        if (enchantIndices.isEmpty()) {
            VillagerEnchanter.LOGGER.warn("No enchanted book trades found for villager!");
            user.displayClientMessage(
                    Component.translatable("message.villager_enchanter.no_enchant_trades")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        // === 读取计数，计算目标 ===
        CompoundTag data = villager.getPersistentData();
        int refreshCount = data.getInt(REFRESH_INDEX_TAG);
        int targetIndex = enchantIndices.get(refreshCount % enchantIndices.size());

        VillagerEnchanter.LOGGER.info("Refreshing trade at index: {}, refresh count: {}", targetIndex, refreshCount);

        // === 刷新 ===
        offers.set(targetIndex, new RefreshTrade(15, holders).getOffer(villager, villager.getRandom()));
        data.putInt(REFRESH_INDEX_TAG, refreshCount + 1);

        // === 消耗 ===
        ItemStack stackInHand = user.getItemInHand(hand);
        if (!stackInHand.isEmpty()) {
            stackInHand.shrink(1);
        }

        // ===== 发送提示信息给玩家 =====
        // 计算当前刷新的位置（从1开始计数）
        int currentPosition = (refreshCount % enchantIndices.size()) + 1;
        int totalCount = enchantIndices.size();

        // 判断是否完成了一轮刷新（回到第一个）
        boolean isCycleComplete = (refreshCount + 1) % enchantIndices.size() == 0;

        // 构建提示消息
        Component message;
        if (isCycleComplete) {
            // 完成一轮刷新
            message = Component.translatable(
                    "message.villager_enchanter.refresh_complete",
                    totalCount
            ).withStyle(ChatFormatting.GOLD);
        } else {
            // 显示当前进度
            message = Component.translatable(
                    "message.villager_enchanter.refresh_progress",
                    currentPosition,
                    totalCount
            ).withStyle(ChatFormatting.GREEN);
        }

        user.displayClientMessage(message, true);

        // 播放音效
        villager.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // ===== 刷新交易类 =====
    static class RefreshTrade implements VillagerTrades.ItemListing {
        private final int villagerXp;
        private final List<Holder<Enchantment>> holders;

        public RefreshTrade(int experience, List<Holder<Enchantment>> holders) {
            this.villagerXp = experience;
            this.holders = holders;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            if (holders == null || holders.isEmpty()) {
                return new MerchantOffer(
                        new ItemCost(Items.EMERALD, 16),
                        Optional.empty(),
                        new ItemStack(Items.ENCHANTED_BOOK, 1),
                        0,
                        12,
                        this.villagerXp,
                        0.2F
                );
            }

            Holder<Enchantment> holder = holders.get(random.nextInt(holders.size()));
            int level = Mth.nextInt(random, holder.value().getMinLevel(), holder.value().getMaxLevel());

            ItemStack enchantedBook = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(holder, level)
            );

            int price = 2 + random.nextInt(5 + level * 10) + 3 * level;
            if (price > 64) price = 64;

            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, price),
                    Optional.of(new ItemCost(Items.BOOK, 1)),
                    enchantedBook,
                    0,
                    12,
                    this.villagerXp,
                    0.2F
            );
        }
    }
}