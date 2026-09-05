package com.muyu.villager_enchanter;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== 附魔师配置 =====
    public static final ModConfigSpec.IntValue BOOK_PRICE = BUILDER
            .comment("附魔师出售附魔刷新书的绿宝石价格")
            .translation("villager_enchanter.config.bookPrice")
            .defineInRange("bookPrice", 16, 1, 64);

    public static final ModConfigSpec.IntValue BOOK_TRADE_COUNT = BUILDER
            .comment("附魔刷新书的最大交易次数")
            .translation("villager_enchanter.config.bookTradeCount")
            .defineInRange("bookTradeCount", 4, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue BOOK_REFRESH_CHANCE = BUILDER
            .comment("5级附魔师出现附魔刷新书的概率 (0.0 ~ 1.0)")
            .translation("villager_enchanter.config.bookRefreshChance")
            .defineInRange("bookRefreshChance", 0.3, 0.0, 1.0);

    // ===== 图书管理员配置 =====
    public static final ModConfigSpec.IntValue LIBRARIAN_BOOK_PRICE = BUILDER
            .comment("图书管理员出售附魔刷新书的绿宝石价格")
            .translation("villager_enchanter.config.librarianBookPrice")
            .defineInRange("librarianBookPrice", 32, 1, 64);

    public static final ModConfigSpec.DoubleValue LIBRARIAN_REFRESH_CHANCE = BUILDER
            .comment("5级图书管理员出现附魔刷新书的概率 (0.0 ~ 1.0)")
            .translation("villager_enchanter.config.librarianRefreshChance")
            .defineInRange("librarianRefreshChance", 0.15, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int bookPrice = 16;
    public static int bookTradeCount = 4;
    public static double bookRefreshChance = 0.3;
    public static int librarianBookPrice = 32;
    public static double librarianRefreshChance = 0.15;

    public static void refresh() {
        bookPrice = BOOK_PRICE.get();
        bookTradeCount = BOOK_TRADE_COUNT.get();
        bookRefreshChance = BOOK_REFRESH_CHANCE.get();
        librarianBookPrice = LIBRARIAN_BOOK_PRICE.get();
        librarianRefreshChance = LIBRARIAN_REFRESH_CHANCE.get();
    }
}