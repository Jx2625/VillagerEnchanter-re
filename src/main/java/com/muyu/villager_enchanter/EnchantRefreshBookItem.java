package com.muyu.villager_enchanter;

import net.minecraft.core.Holder;
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

import java.util.List;
import java.util.Optional;

public class EnchantRefreshBookItem extends Item {

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
        if (!world.isClientSide) {
            // 使用 VillagerEnchanter 中的静态列表
            List<Holder<Enchantment>> holders = VillagerEnchanter.getEnchantmentHolders();

            if (holders.isEmpty()) {
                return InteractionResult.FAIL;
            }

            MerchantOffers offers = villager.getOffers();
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer tradeOffer = offers.get(i);
                ItemStack sellItem = tradeOffer.getResult();

                if (tradeOffer.getBaseCostA().getItem() == Items.EMERALD &&
                        tradeOffer.getCostB().getItem() == Items.BOOK &&
                        sellItem.getItem() == Items.ENCHANTED_BOOK) {

                    ItemStack stackInHand = user.getItemInHand(hand);
                    if (stackInHand.isEmpty()) {
                        break;
                    }
                    offers.set(i, new RefreshTrade(15, holders).getOffer(villager, villager.getRandom()));
                    stackInHand.shrink(1);
                    break;
                }
            }
            villager.setOffers(offers);
        }

        villager.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    static class RefreshTrade implements VillagerTrades.ItemListing {
        private final int villagerXp;
        private final List<Holder<Enchantment>> holders;

        public RefreshTrade(int experience, List<Holder<Enchantment>> holders) {
            this.villagerXp = experience;
            this.holders = holders;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            if (holders.isEmpty()) {
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