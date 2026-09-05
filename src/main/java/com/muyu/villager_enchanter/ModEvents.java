package com.muyu.villager_enchanter;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = VillagerEnchanter.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        // ===== 1. 给原版图书管理员添加附魔刷新书交易 =====
        if (event.getType() == VillagerProfession.LIBRARIAN) {
            VillagerEnchanter.LOGGER.info("Adding Enchant Refresh Book trade to Librarian");

            event.getTrades().get(5).add((entity, random) -> {
                if (random.nextFloat() < Config.librarianRefreshChance) {
                    ItemStack refreshBook = new ItemStack(VillagerEnchanter.ENCHANT_REFRESH_BOOK.get());
                    return new MerchantOffer(
                            new ItemCost(Items.EMERALD, Config.librarianBookPrice),
                            Optional.of(new ItemCost(Items.BOOK, 1)),
                            refreshBook,
                            0,
                            Config.bookTradeCount,
                            15,
                            0.05F
                    );
                } else {
                    return createEnchantedBookOffer(entity, random);
                }
            });

            VillagerEnchanter.LOGGER.info("Added Enchant Refresh Book to Librarian level 5");
            return;
        }

        // ===== 2. 给自定义附魔师添加交易 =====
        if (event.getType() != VillagerEnchanter.ENCHANTER_PROFESSION.get()) {
            return;
        }

        VillagerEnchanter.LOGGER.info("SetupTrades: Adding trades for Enchanter profession");

        VillagerEnchanter.getEnchantmentHolders(event.getRegistryAccess());

        // ===== 1级：青金石 → 绿宝石 =====
        event.getTrades().get(1).add((entity, random) ->
                new MerchantOffer(
                        new ItemCost(Items.LAPIS_LAZULI, 8),
                        Optional.empty(),
                        new ItemStack(Items.EMERALD, 16),
                        0,
                        12,
                        15,
                        0.05F
                ));

        // ===== 为每个等级添加附魔书 =====
        for (int level : event.getTrades().keySet()) {
            int bookCount = (level == 5) ? 8 : level;

            for (int i = 0; i < bookCount; i++) {
                event.getTrades().get(level).add(new EnchantedBookTrade(15));
            }
        }

        // ===== 5级：附魔刷新书 =====
        event.getTrades().get(5).add((entity, random) -> {
            if (random.nextFloat() < Config.bookRefreshChance) {
                ItemStack refreshBook = new ItemStack(VillagerEnchanter.ENCHANT_REFRESH_BOOK.get());
                return new MerchantOffer(
                        new ItemCost(Items.EMERALD, Config.bookPrice),
                        Optional.of(new ItemCost(Items.BOOK, 1)),
                        refreshBook,
                        0,
                        Config.bookTradeCount,
                        15,
                        0.05F
                );
            } else {
                return new EnchantedBookTrade(15).getOffer(entity, random);
            }
        });

        VillagerEnchanter.LOGGER.info("Added Refresh Book trade to Enchanter level 5");
    }

    /**
     * 创建附魔书交易
     */
    private static MerchantOffer createEnchantedBookOffer(Entity entity, RandomSource random) {
        List<Holder<Enchantment>> holders = VillagerEnchanter.getEnchantmentHolders();
        if (holders.isEmpty()) {
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, 16),
                    Optional.of(new ItemCost(Items.BOOK, 1)),
                    new ItemStack(Items.ENCHANTED_BOOK, 1),
                    12,
                    15,
                    0.2F
            );
        }

        Holder<Enchantment> holder = holders.get(random.nextInt(holders.size()));
        int level = Mth.nextInt(random, holder.value().getMinLevel(), holder.value().getMaxLevel());

        ItemStack book = EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(holder, level));

        int price = 2 + random.nextInt(5 + level * 10) + 3 * level;
        if (price > 64) price = 64;

        return new MerchantOffer(
                new ItemCost(Items.EMERALD, price),
                Optional.of(new ItemCost(Items.BOOK, 1)),
                book,
                12,
                15,
                0.2F
        );
    }

    /**
     * 附魔书交易类
     */
    private static class EnchantedBookTrade implements VillagerTrades.ItemListing {
        private final int villagerXp;

        public EnchantedBookTrade(int villagerXp) {
            this.villagerXp = villagerXp;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            return createEnchantedBookOffer(entity, random);
        }
    }
}