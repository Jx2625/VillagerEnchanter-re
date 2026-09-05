package com.muyu.villager_enchanter;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Mod(VillagerEnchanter.MODID)
public class VillagerEnchanter {

    public static final String MODID = "villager_enchanter";
    public static final Logger LOGGER = LoggerFactory.getLogger(VillagerEnchanter.class);

    // 使用 ConcurrentHashMap 作为缓存，线程安全
    private static final ConcurrentHashMap<String, List<Holder<Enchantment>>> ENCHANTMENT_CACHE = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "enchantments";

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

        // ===== 关键修改：使用 SERVER 类型 =====
        // 这样在 Configured 中会显示为 "服务端配置文件"
        // 单人模式：内置服务端，可以正常修改
        // 多人模式：只有服务器可以修改
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modEventBus.addListener(this::addCreativeTab);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        LOGGER.info("=== VillagerEnchanter INIT DONE ===");
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(ENCHANT_REFRESH_BOOK.get()));
        }
    }

    /**
     * 服务器启动时刷新配置
     */
    private void onServerStarting(ServerStartingEvent event) {
        Config.refresh();
        LOGGER.info("=== Server Config Applied ===");
        LOGGER.info("bookPrice: {}", Config.bookPrice);
        LOGGER.info("bookTradeCount: {}", Config.bookTradeCount);
        LOGGER.info("bookRefreshChance: {}", Config.bookRefreshChance);
        LOGGER.info("librarianBookPrice: {}", Config.librarianBookPrice);
        LOGGER.info("librarianRefreshChance: {}", Config.librarianRefreshChance);
    }

    /**
     * 获取附魔列表（线程安全，使用缓存）
     */
    public static List<Holder<Enchantment>> getEnchantmentHolders(RegistryAccess registryAccess) {
        List<Holder<Enchantment>> cached = ENCHANTMENT_CACHE.get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }

        Registry<Enchantment> registry = registryAccess.registry(Registries.ENCHANTMENT)
                .orElse(null);

        if (registry == null) {
            LOGGER.error("Enchantment registry not found!");
            return Collections.emptyList();
        }

        List<Holder<Enchantment>> tempList = new ArrayList<>();
        for (Holder<Enchantment> holder : registry.holders().toList()) {
            tempList.add(holder);
        }

        List<Holder<Enchantment>> immutableList = Collections.unmodifiableList(tempList);
        List<Holder<Enchantment>> existing = ENCHANTMENT_CACHE.putIfAbsent(CACHE_KEY, immutableList);

        if (existing != null) {
            return existing;
        }

        LOGGER.info("Loaded {} enchantments into cache", immutableList.size());
        return immutableList;
    }

    /**
     * 获取附魔列表（无参版本）
     */
    public static List<Holder<Enchantment>> getEnchantmentHolders() {
        List<Holder<Enchantment>> cached = ENCHANTMENT_CACHE.get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        LOGGER.warn("Enchantment cache not initialized! Call getEnchantmentHolders(RegistryAccess) first.");
        return Collections.emptyList();
    }

    /**
     * 强制刷新附魔缓存
     */
    public static void refreshEnchantmentCache(RegistryAccess registryAccess) {
        ENCHANTMENT_CACHE.remove(CACHE_KEY);
        getEnchantmentHolders(registryAccess);
        LOGGER.info("Refreshed enchantment cache");
    }
}