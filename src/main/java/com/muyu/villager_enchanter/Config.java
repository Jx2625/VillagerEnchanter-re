package com.muyu.villager_enchanter;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BOOK_PRICE = BUILDER
            .translation("villager_enchanter.config.bookPrice")
            .defineInRange("bookPrice", 16, 1, 64);

    public static final ModConfigSpec.IntValue BOOK_TRADE_COUNT = BUILDER
            .translation("villager_enchanter.config.bookTradeCount")
            .defineInRange("bookTradeCount", 4, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue BOOK_REFRESH_CHANCE = BUILDER
            .translation("villager_enchanter.config.bookRefreshChance")
            .defineInRange("bookRefreshChance", 0.3, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int bookPrice = 16;
    public static int bookTradeCount = 4;
    public static double bookRefreshChance = 0.3;

    public static void refresh() {
        bookPrice = BOOK_PRICE.get();
        bookTradeCount = BOOK_TRADE_COUNT.get();
        bookRefreshChance = BOOK_REFRESH_CHANCE.get();
    }
}