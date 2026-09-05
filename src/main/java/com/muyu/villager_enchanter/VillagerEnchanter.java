package com.muyu.villager_enchanter;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Mod(VillagerEnchanter.MODID)
public class VillagerEnchanter {

    public static final String MODID = "villager_enchanter";
    private static final Logger LOGGER = LoggerFactory.getLogger(VillagerEnchanter.class);

    private static List<Holder<Enchantment>> ENCHANTMENT_HOLDERS = new ArrayList<>();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final Supplier<Item> ENCHANT_REFRESH_BOOK = ITEMS.register("enchant_refresh_book",
            () -> new EnchantRefreshBookItem(new Item.Properties().stacksTo(16)));

    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, MODID);
    public static final Supplier<PoiType> ENCHANTING_TABLE_POI_TYPE = POI_TYPES.register("enchanter",
            () -> new PoiType(ImmutableSet.of(Blocks.ENCHANTING_TABLE.defaultBlockState()), 1, 1));

    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, MODID);
    public static final Supplier<VillagerProfession> ENCHANTER_PROFESSION = VILLAGER_PROFESSIONS.register("enchanter",
            () -> new VillagerProfession(
                    ResourceLocation.fromNamespaceAndPath(MODID, "enchanter").toString(),
                    holder -> holder.value() == ENCHANTING_TABLE_POI_TYPE.get(),
                    holder -> holder.value() == ENCHANTING_TABLE_POI_TYPE.get(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN
            ));

    public VillagerEnchanter(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("=== VillagerEnchanter INIT ===");

        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        VILLAGER_PROFESSIONS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(this::addCreativeTab);
        NeoForge.EVENT_BUS.addListener(this::setupTrades);

        LOGGER.info("=== VillagerEnchanter INIT DONE ===");
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(ENCHANT_REFRESH_BOOK.get()));
        }
    }

    private void setupTrades(VillagerTradesEvent event) {
        if (event.getType() != ENCHANTER_PROFESSION.get()) {
            return;
        }

        LOGGER.info("SetupTrades: Adding trades for Enchanter profession");

        Registry<Enchantment> registry = event.getRegistryAccess()
                .registry(Registries.ENCHANTMENT)
                .orElse(null);

        if (registry == null) {
            LOGGER.error("Enchantment registry not found!");
            return;
        }

        if (ENCHANTMENT_HOLDERS.isEmpty()) {
            for (Holder<Enchantment> holder : registry.asHolderIdMap()) {
                ENCHANTMENT_HOLDERS.add(holder);
            }
            LOGGER.info("Initialized ENCHANTMENT_HOLDERS with {} enchantments", ENCHANTMENT_HOLDERS.size());
        }

        if (ENCHANTMENT_HOLDERS.isEmpty()) {
            LOGGER.error("No enchantments found!");
            return;
        }

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

        // ===== 为每个等级添加交易 =====
        for (int level : event.getTrades().keySet()) {
            int bookCount = (level == 5) ? 8 : 2 + level;

            // 添加附魔书
            for (int i = 0; i < bookCount; i++) {
                event.getTrades().get(level).add(new AllEnchantBookTrade(15));
            }

            // 5级：使用配置的概率添加附魔刷新书
            if (level == 5) {
                RandomSource random = RandomSource.create();
                if (random.nextFloat() < Config.bookRefreshChance) {
                    event.getTrades().get(5).add(new RefreshBookTrade());
                    LOGGER.info("Added Refresh Book to level 5 (chance: {}%)", Config.bookRefreshChance * 100);
                } else {
                    event.getTrades().get(5).add(new AllEnchantBookTrade(15));
                    LOGGER.info("No Refresh Book this time");
                }
            }

            LOGGER.info("Level {} total trades: {}", level, event.getTrades().get(level).size());
        }
    }

    // ===== 附魔书交易类 =====
    private static class AllEnchantBookTrade implements VillagerTrades.ItemListing {
        private final int villagerXp;

        public AllEnchantBookTrade(int villagerXp) {
            this.villagerXp = villagerXp;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            if (ENCHANTMENT_HOLDERS.isEmpty()) {
                return new MerchantOffer(
                        new ItemCost(Items.EMERALD, 16),
                        Optional.empty(),
                        new ItemStack(Items.ENCHANTED_BOOK, 1),
                        12,
                        villagerXp,
                        0.2F
                );
            }

            Holder<Enchantment> holder = ENCHANTMENT_HOLDERS.get(random.nextInt(ENCHANTMENT_HOLDERS.size()));
            Enchantment enchantment = holder.value();
            int level = Mth.nextInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());

            ItemStack book = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(holder, level));

            int price = 2 + random.nextInt(5 + level * 10) + 3 * level;
            if (price > 64) price = 64;

            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, price),
                    Optional.of(new ItemCost(Items.BOOK, 1)),
                    book,
                    12,
                    villagerXp,
                    0.2F
            );
        }
    }

    // ===== 附魔刷新书交易类 =====
    private static class RefreshBookTrade implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
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
        }
    }

    public static List<Holder<Enchantment>> getEnchantmentHolders() {
        return ENCHANTMENT_HOLDERS;
    }
}