package com.imoonday.villager_enchanter;

import com.google.common.collect.ImmutableSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mod(VillagerEnchanter.MODID)
public class VillagerEnchanter {

    public static final String MODID = "villager_enchanter";

    // ========== 物品注册 ==========
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> ENCHANT_REFRESH_BOOK = ITEMS.register("enchant_refresh_book",
            () -> new EnchantRefreshBookItem(new Item.Properties().stacksTo(16)));

    // ========== POI 注册（附魔台） ==========
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, MODID);
    public static final RegistryObject<PoiType> ENCHANTING_TABLE_POI_TYPE = POI_TYPES.register("enchanter",
            () -> new PoiType(Set.of(Blocks.ENCHANTING_TABLE.defaultBlockState()), 1, 1));

    // ========== 村民职业注册（附魔师） ==========
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, MODID);
    public static final RegistryObject<VillagerProfession> ENCHANTER_PROFESSION = VILLAGER_PROFESSIONS.register("enchanter",
            () -> new VillagerProfession("enchanter",
                    poiTypeHolder -> poiTypeHolder.is(ENCHANTING_TABLE_POI_TYPE.getId()),
                    poiTypeHolder -> poiTypeHolder.is(ENCHANTING_TABLE_POI_TYPE.getId()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    public VillagerEnchanter() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        VILLAGER_PROFESSIONS.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 注册村民交易事件监听
        MinecraftForge.EVENT_BUS.addListener(this::villagerTrades);
    }

    // ========== 附魔师交易：出售所有附魔书 ==========
    private void villagerTrades(VillagerTradesEvent event) {
        // 只处理附魔师职业，不修改图书管理员
        if (event.getType() == ENCHANTER_PROFESSION.get()) {

            List<Enchantment> enchantments = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());

            for (int level : event.getTrades().keySet()) {
                List<VillagerTrades.ItemListing> trades = event.getTrades().get(level);

                for (int i = 0; i < trades.size(); i++) {
                    // 将原版的附魔书交易替换为全附魔版本
                    if (trades.get(i) instanceof VillagerTrades.EnchantBookForEmeralds itemListing) {
                        trades.remove(i);
                        trades.add(i, new AllEnchantBooksForEmeralds(itemListing.villagerXp, enchantments));
                    }
                }
            }
        }
    }

    // ========== 内部类：全附魔交易实现 ==========
    private static class AllEnchantBooksForEmeralds implements VillagerTrades.ItemListing {
        private final int villagerXp;
        private final List<Enchantment> enchantments;

        public AllEnchantBooksForEmeralds(int villagerXp, List<Enchantment> enchantments) {
            this.villagerXp = villagerXp;
            this.enchantments = enchantments;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource randomSource) {
            // 随机选择一个附魔
            Enchantment enchantment = enchantments.get(randomSource.nextInt(enchantments.size()));

            // 随机等级
            int level = Mth.nextInt(randomSource, enchantment.getMinLevel(), enchantment.getMaxLevel());

            // 创建附魔书
            ItemStack itemstack = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(enchantment, level));

            // 计算价格
            int cost = 2 + randomSource.nextInt(5 + level * 10) + 3 * level;

            // 宝藏附魔价格翻倍
            if (enchantment.isTreasureOnly()) {
                cost *= 2;
            }

            // 上限64
            if (cost > 64) {
                cost = 64;
            }

            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, cost),   // 绿宝石
                    new ItemStack(Items.BOOK),            // 书
                    itemstack,                             // 附魔书
                    12,                                    // 最大交易次数
                    villagerXp,                            // 村民经验
                    0.2F                                   // 价格浮动
            );
        }
    }
}