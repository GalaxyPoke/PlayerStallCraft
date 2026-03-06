package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemSelectGUI implements Listener {

    // ── Category definitions ──────────────────────────────────────────────────
    private static final LinkedHashMap<String, CategoryDef> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("矿物与锭", new CategoryDef(Material.DIAMOND,
            Material.DIAMOND, Material.DIAMOND_BLOCK, Material.IRON_INGOT, Material.IRON_BLOCK,
            Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.EMERALD, Material.EMERALD_BLOCK,
            Material.COAL, Material.COAL_BLOCK, Material.COPPER_INGOT, Material.COPPER_BLOCK,
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.LAPIS_LAZULI, Material.LAPIS_BLOCK,
            Material.REDSTONE, Material.REDSTONE_BLOCK, Material.RAW_IRON, Material.RAW_GOLD,
            Material.RAW_COPPER, Material.AMETHYST_SHARD, Material.QUARTZ));

        CATEGORIES.put("原木与木板", new CategoryDef(Material.OAK_LOG,
            Material.OAK_LOG, Material.OAK_PLANKS, Material.BIRCH_LOG, Material.BIRCH_PLANKS,
            Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.JUNGLE_LOG, Material.JUNGLE_PLANKS,
            Material.ACACIA_LOG, Material.ACACIA_PLANKS, Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS,
            Material.CHERRY_LOG, Material.CHERRY_PLANKS, Material.MANGROVE_LOG, Material.MANGROVE_PLANKS,
            Material.BAMBOO_BLOCK, Material.BAMBOO_PLANKS, Material.CRIMSON_STEM, Material.CRIMSON_PLANKS,
            Material.WARPED_STEM, Material.WARPED_PLANKS));

        CATEGORIES.put("建材与石头", new CategoryDef(Material.STONE,
            Material.STONE, Material.COBBLESTONE, Material.DIRT, Material.GRASS_BLOCK,
            Material.SAND, Material.GRAVEL, Material.CLAY, Material.CLAY_BALL,
            Material.BRICK, Material.BRICKS, Material.STONE_BRICKS, Material.MOSSY_COBBLESTONE,
            Material.MOSSY_STONE_BRICKS, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE,
            Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE, Material.BASALT, Material.SMOOTH_BASALT,
            Material.BLACKSTONE, Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL,
            Material.GLOWSTONE, Material.END_STONE, Material.PURPUR_BLOCK));

        CATEGORIES.put("食物", new CategoryDef(Material.BREAD,
            Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
            Material.BREAD, Material.WHEAT, Material.CARROT, Material.GOLDEN_CARROT,
            Material.POTATO, Material.BAKED_POTATO, Material.BEETROOT, Material.MELON_SLICE,
            Material.PUMPKIN, Material.PUMPKIN_PIE, Material.COOKIE, Material.CAKE,
            Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON,
            Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON,
            Material.TROPICAL_FISH, Material.PUFFERFISH, Material.RABBIT, Material.COOKED_RABBIT,
            Material.RABBIT_STEW, Material.MUSHROOM_STEW, Material.BEETROOT_SOUP,
            Material.HONEY_BOTTLE, Material.DRIED_KELP, Material.SWEET_BERRIES,
            Material.CHORUS_FRUIT, Material.ROTTEN_FLESH, Material.SPIDER_EYE));

        CATEGORIES.put("工具与武器", new CategoryDef(Material.DIAMOND_SWORD,
            Material.WOODEN_SWORD, Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE,
            Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE,
            Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE,
            Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE,
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.ARROW,
            Material.SPECTRAL_ARROW, Material.FISHING_ROD, Material.FLINT_AND_STEEL,
            Material.SHEARS, Material.LEAD, Material.NAME_TAG, Material.BRUSH, Material.MACE));

        CATEGORIES.put("护甲", new CategoryDef(Material.DIAMOND_CHESTPLATE,
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.TURTLE_HELMET, Material.ELYTRA, Material.SHIELD,
            Material.TOTEM_OF_UNDYING, Material.WOLF_ARMOR));

        CATEGORIES.put("功能方块", new CategoryDef(Material.CHEST,
            Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.CRAFTING_TABLE, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.ANVIL, Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.BEACON,
            Material.GRINDSTONE, Material.STONECUTTER, Material.CARTOGRAPHY_TABLE,
            Material.FLETCHING_TABLE, Material.SMITHING_TABLE, Material.LOOM, Material.COMPOSTER,
            Material.LECTERN, Material.BELL, Material.JUKEBOX, Material.NOTE_BLOCK,
            Material.DISPENSER, Material.DROPPER, Material.HOPPER, Material.OBSERVER,
            Material.PISTON, Material.STICKY_PISTON, Material.REDSTONE_LAMP,
            Material.TNT, Material.RESPAWN_ANCHOR, Material.CRAFTER, Material.VAULT));

        CATEGORIES.put("红石", new CategoryDef(Material.REDSTONE,
            Material.REDSTONE_TORCH, Material.LEVER, Material.REPEATER, Material.COMPARATOR,
            Material.TRIPWIRE_HOOK, Material.STONE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.DAYLIGHT_DETECTOR, Material.TARGET,
            Material.SCULK_SENSOR, Material.CALIBRATED_SCULK_SENSOR));

        CATEGORIES.put("药水与功能道具", new CategoryDef(Material.POTION,
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.GLASS_BOTTLE, Material.EXPERIENCE_BOTTLE, Material.DRAGON_BREATH,
            Material.BLAZE_POWDER, Material.BLAZE_ROD, Material.GHAST_TEAR, Material.MAGMA_CREAM,
            Material.NETHER_WART, Material.FERMENTED_SPIDER_EYE, Material.GLISTERING_MELON_SLICE,
            Material.RABBIT_FOOT, Material.PHANTOM_MEMBRANE, Material.TURTLE_SCUTE,
            Material.COMPASS, Material.RECOVERY_COMPASS, Material.CLOCK, Material.SPYGLASS,
            Material.ENDER_PEARL, Material.ENDER_EYE, Material.END_CRYSTAL,
            Material.NETHER_STAR, Material.HEART_OF_THE_SEA, Material.NAUTILUS_SHELL,
            Material.ECHO_SHARD, Material.BREEZE_ROD, Material.WIND_CHARGE));

        CATEGORIES.put("装饰与其他方块", new CategoryDef(Material.TORCH,
            Material.TORCH, Material.SOUL_TORCH, Material.LANTERN, Material.SOUL_LANTERN,
            Material.CANDLE, Material.SEA_LANTERN, Material.SHROOMLIGHT, Material.JACK_O_LANTERN,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CHAIN, Material.IRON_BARS,
            Material.GLASS, Material.GLASS_PANE, Material.TINTED_GLASS,
            Material.FLOWER_POT, Material.PAINTING, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
            Material.ARMOR_STAND, Material.DECORATED_POT, Material.END_ROD,
            Material.LADDER, Material.SCAFFOLDING, Material.BONE_BLOCK));

        CATEGORIES.put("花卉与植物", new CategoryDef(Material.DANDELION,
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP,
            Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY,
            Material.WITHER_ROSE, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.TORCHFLOWER, Material.PINK_PETALS,
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.SPRUCE_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.CHERRY_SAPLING, Material.BAMBOO, Material.SUGAR_CANE, Material.CACTUS,
            Material.VINE, Material.LILY_PAD, Material.KELP, Material.MOSS_BLOCK,
            Material.MOSS_CARPET, Material.AZALEA, Material.FLOWERING_AZALEA,
            Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF, Material.DEAD_BUSH));

        CATEGORIES.put("染料与羊毛", new CategoryDef(Material.WHITE_DYE,
            Material.WHITE_DYE, Material.ORANGE_DYE, Material.MAGENTA_DYE, Material.LIGHT_BLUE_DYE,
            Material.YELLOW_DYE, Material.LIME_DYE, Material.PINK_DYE, Material.GRAY_DYE,
            Material.LIGHT_GRAY_DYE, Material.CYAN_DYE, Material.PURPLE_DYE, Material.BLUE_DYE,
            Material.BROWN_DYE, Material.GREEN_DYE, Material.RED_DYE, Material.BLACK_DYE,
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL));

        CATEGORIES.put("交通与容器", new CategoryDef(Material.CHEST_MINECART,
            Material.MINECART, Material.CHEST_MINECART, Material.FURNACE_MINECART,
            Material.TNT_MINECART, Material.HOPPER_MINECART,
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL,
            Material.OAK_BOAT, Material.BIRCH_BOAT, Material.SPRUCE_BOAT, Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT, Material.DARK_OAK_BOAT, Material.CHERRY_BOAT,
            Material.MANGROVE_BOAT, Material.BAMBOO_RAFT, Material.SADDLE,
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX, Material.BUCKET, Material.WATER_BUCKET, Material.LAVA_BUCKET));

        CATEGORIES.put("书籍与附魔", new CategoryDef(Material.ENCHANTED_BOOK,
            Material.BOOK, Material.WRITABLE_BOOK, Material.WRITTEN_BOOK,
            Material.ENCHANTED_BOOK, Material.KNOWLEDGE_BOOK,
            Material.BOOKSHELF, Material.CHISELED_BOOKSHELF,
            Material.PAPER, Material.INK_SAC, Material.GLOW_INK_SAC, Material.FEATHER));

        CATEGORIES.put("其他物品", new CategoryDef(Material.BONE,
            Material.BONE, Material.BONE_MEAL, Material.STRING, Material.SLIME_BALL, Material.SLIME_BLOCK,
            Material.HONEY_BLOCK, Material.HONEYCOMB, Material.HONEYCOMB_BLOCK,
            Material.LEATHER, Material.RABBIT_HIDE, Material.GUNPOWDER,
            Material.FIREWORK_ROCKET, Material.FIREWORK_STAR,
            Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS,
            Material.EGG, Material.SNOWBALL, Material.FIRE_CHARGE,
            Material.NETHER_BRICK, Material.GOAT_HORN,
            Material.TRIAL_KEY, Material.OMINOUS_TRIAL_KEY, Material.OMINOUS_BOTTLE));

        // ─ Cobblemon 分类（通过 matchMaterial 解析命名空间 ID）──────────────────
        addCobblemon("精灵球", "cobblemon:poke_ball",
            "cobblemon:poke_ball", "cobblemon:great_ball", "cobblemon:ultra_ball", "cobblemon:master_ball",
            "cobblemon:safari_ball", "cobblemon:fast_ball", "cobblemon:level_ball", "cobblemon:lure_ball",
            "cobblemon:heavy_ball", "cobblemon:love_ball", "cobblemon:friend_ball", "cobblemon:moon_ball",
            "cobblemon:sport_ball", "cobblemon:park_ball", "cobblemon:net_ball", "cobblemon:dive_ball",
            "cobblemon:nest_ball", "cobblemon:repeat_ball", "cobblemon:timer_ball", "cobblemon:luxury_ball",
            "cobblemon:dusk_ball", "cobblemon:heal_ball", "cobblemon:quick_ball", "cobblemon:dream_ball",
            "cobblemon:beast_ball", "cobblemon:cherish_ball", "cobblemon:premier_ball",
            "cobblemon:citrine_ball", "cobblemon:verdant_ball", "cobblemon:azure_ball",
            "cobblemon:roseate_ball", "cobblemon:slate_ball",
            "cobblemon:ancient_poke_ball", "cobblemon:ancient_great_ball", "cobblemon:ancient_ultra_ball",
            "cobblemon:ancient_feather_ball", "cobblemon:ancient_wing_ball", "cobblemon:ancient_jet_ball",
            "cobblemon:ancient_heavy_ball", "cobblemon:ancient_leaden_ball",
            "cobblemon:ancient_gigaton_ball", "cobblemon:ancient_origin_ball");

        addCobblemon("宝可梦恢复道具", "cobblemon:rare_candy",
            "cobblemon:potion", "cobblemon:super_potion", "cobblemon:hyper_potion",
            "cobblemon:max_potion", "cobblemon:full_restore", "cobblemon:revive", "cobblemon:max_revive",
            "cobblemon:antidote", "cobblemon:awakening", "cobblemon:burn_heal",
            "cobblemon:ice_heal", "cobblemon:paralyze_heal", "cobblemon:full_heal",
            "cobblemon:ether", "cobblemon:max_ether", "cobblemon:elixir", "cobblemon:max_elixir",
            "cobblemon:pp_up", "cobblemon:pp_max", "cobblemon:rare_candy",
            "cobblemon:exp_candy_xs", "cobblemon:exp_candy_s", "cobblemon:exp_candy_m",
            "cobblemon:exp_candy_l", "cobblemon:exp_candy_xl",
            "cobblemon:x_attack", "cobblemon:x_defense", "cobblemon:x_sp_atk",
            "cobblemon:x_sp_def", "cobblemon:x_speed", "cobblemon:x_accuracy",
            "cobblemon:dire_hit", "cobblemon:guard_spec",
            "cobblemon:hp_up", "cobblemon:protein", "cobblemon:iron",
            "cobblemon:calcium", "cobblemon:zinc", "cobblemon:carbos");

        addCobblemon("进化道具", "cobblemon:fire_stone",
            "cobblemon:fire_stone", "cobblemon:water_stone", "cobblemon:thunder_stone",
            "cobblemon:leaf_stone", "cobblemon:moon_stone", "cobblemon:sun_stone",
            "cobblemon:shiny_stone", "cobblemon:dusk_stone", "cobblemon:dawn_stone",
            "cobblemon:ice_stone", "cobblemon:oval_stone", "cobblemon:everstone",
            "cobblemon:kings_rock", "cobblemon:metal_coat", "cobblemon:dragon_scale",
            "cobblemon:upgrade", "cobblemon:dubious_disc", "cobblemon:protector",
            "cobblemon:electirizer", "cobblemon:magmarizer", "cobblemon:razor_fang",
            "cobblemon:razor_claw", "cobblemon:reaper_cloth", "cobblemon:prism_scale",
            "cobblemon:whipped_dream", "cobblemon:sachet", "cobblemon:deep_sea_tooth",
            "cobblemon:deep_sea_scale", "cobblemon:link_cable", "cobblemon:linking_cord");

        addCobblemon("携带道具", "cobblemon:life_orb",
            "cobblemon:leftovers", "cobblemon:black_sludge", "cobblemon:rocky_helmet",
            "cobblemon:assault_vest", "cobblemon:focus_sash", "cobblemon:choice_band",
            "cobblemon:choice_specs", "cobblemon:choice_scarf", "cobblemon:life_orb",
            "cobblemon:expert_belt", "cobblemon:muscle_band", "cobblemon:wise_glasses",
            "cobblemon:scope_lens", "cobblemon:wide_lens", "cobblemon:zoom_lens",
            "cobblemon:grip_claw", "cobblemon:shell_bell", "cobblemon:soothe_bell",
            "cobblemon:destiny_knot", "cobblemon:lucky_egg", "cobblemon:amulet_coin",
            "cobblemon:exp_share", "cobblemon:quick_claw",
            "cobblemon:power_weight", "cobblemon:power_bracer", "cobblemon:power_belt",
            "cobblemon:power_lens", "cobblemon:power_band", "cobblemon:power_anklet",
            "cobblemon:silk_scarf", "cobblemon:miracle_seed", "cobblemon:charcoal",
            "cobblemon:mystic_water", "cobblemon:magnet", "cobblemon:never_melt_ice",
            "cobblemon:black_belt", "cobblemon:poison_barb", "cobblemon:soft_sand",
            "cobblemon:sharp_beak", "cobblemon:twisted_spoon", "cobblemon:silver_powder",
            "cobblemon:hard_stone", "cobblemon:spell_tag", "cobblemon:dragon_fang",
            "cobblemon:black_glasses", "cobblemon:fairy_feather");

        addCobblemon("树果", "cobblemon:oran_berry",
            "cobblemon:oran_berry", "cobblemon:sitrus_berry", "cobblemon:leppa_berry",
            "cobblemon:cheri_berry", "cobblemon:chesto_berry", "cobblemon:pecha_berry",
            "cobblemon:rawst_berry", "cobblemon:aspear_berry", "cobblemon:persim_berry",
            "cobblemon:lum_berry", "cobblemon:figy_berry", "cobblemon:wiki_berry",
            "cobblemon:mago_berry", "cobblemon:aguav_berry", "cobblemon:iapapa_berry",
            "cobblemon:razz_berry", "cobblemon:bluk_berry", "cobblemon:nanab_berry",
            "cobblemon:wepear_berry", "cobblemon:pinap_berry", "cobblemon:pomeg_berry",
            "cobblemon:kelpsy_berry", "cobblemon:qualot_berry", "cobblemon:hondew_berry",
            "cobblemon:grepa_berry", "cobblemon:tamato_berry", "cobblemon:occa_berry",
            "cobblemon:passho_berry", "cobblemon:wacan_berry", "cobblemon:rindo_berry",
            "cobblemon:yache_berry", "cobblemon:chople_berry", "cobblemon:liechi_berry",
            "cobblemon:salac_berry", "cobblemon:petaya_berry", "cobblemon:starf_berry");

        addCobblemon("芒果与薄荷", "cobblemon:red_apricorn",
            "cobblemon:red_apricorn", "cobblemon:yellow_apricorn", "cobblemon:green_apricorn",
            "cobblemon:blue_apricorn", "cobblemon:pink_apricorn", "cobblemon:black_apricorn",
            "cobblemon:white_apricorn",
            "cobblemon:red_apricorn_seed", "cobblemon:yellow_apricorn_seed",
            "cobblemon:green_apricorn_seed", "cobblemon:blue_apricorn_seed",
            "cobblemon:pink_apricorn_seed", "cobblemon:black_apricorn_seed",
            "cobblemon:white_apricorn_seed",
            "cobblemon:lonely_mint", "cobblemon:brave_mint", "cobblemon:adamant_mint",
            "cobblemon:naughty_mint", "cobblemon:bold_mint", "cobblemon:relaxed_mint",
            "cobblemon:impish_mint", "cobblemon:modest_mint", "cobblemon:mild_mint",
            "cobblemon:quiet_mint", "cobblemon:calm_mint", "cobblemon:gentle_mint",
            "cobblemon:sassy_mint", "cobblemon:careful_mint", "cobblemon:timid_mint",
            "cobblemon:hasty_mint", "cobblemon:jolly_mint", "cobblemon:naive_mint",
            "cobblemon:serious_mint");

        addCobblemon("化石与其他", "cobblemon:relic_coin",
            "cobblemon:helix_fossil", "cobblemon:dome_fossil", "cobblemon:old_amber",
            "cobblemon:root_fossil", "cobblemon:claw_fossil", "cobblemon:skull_fossil",
            "cobblemon:armor_fossil", "cobblemon:cover_fossil", "cobblemon:plume_fossil",
            "cobblemon:jaw_fossil", "cobblemon:sail_fossil",
            "cobblemon:fossilized_bird", "cobblemon:fossilized_fish",
            "cobblemon:fossilized_drake", "cobblemon:fossilized_dino",
            "cobblemon:relic_coin", "cobblemon:relic_coin_pouch", "cobblemon:relic_coin_sack",
            "cobblemon:tumblestone", "cobblemon:black_tumblestone", "cobblemon:sky_tumblestone",
            "cobblemon:iron_chunk", "cobblemon:black_augurite", "cobblemon:peat_block",
            "cobblemon:pokemon_model",
            "cobblemon:healing_machine", "cobblemon:pc", "cobblemon:pasture",
            "cobblemon:apricorn_log", "cobblemon:apricorn_planks",
            "cobblemon:fossil_analyzer", "cobblemon:restoration_tank",
            "cobblemon:pokedex_red", "cobblemon:pokedex_yellow", "cobblemon:pokedex_green",
            "cobblemon:pokedex_blue", "cobblemon:pokedex_pink",
            "cobblemon:pokedex_black", "cobblemon:pokedex_white");
    }

    private static Material tryResolveMaterial(String key) {
        // 1. 直接匹配（对 vanilla 物品有效）
        Material m = Material.matchMaterial(key);
        if (m != null && m.isItem()) return m;
        // 2. 将命名空间冒号替换为下划线后匹配
        //    Bukkit 的 matchMaterial 会把非单词字符删除，导致 "cobblemon:poke_ball"
        //    变成 "COBBLEMONPOKE_BALL"，而 Mohist 混合服注册的实际名称是
        //    "COBBLEMON_POKE_BALL"，所以先做替换再匹配
        if (key.contains(":")) {
            m = Material.matchMaterial(key.replace(":", "_"));
            if (m != null && m.isItem()) return m;
        }
        return null;
    }

    private static void addCobblemon(String name, String iconKey, String... keys) {
        List<Material> mats = new ArrayList<>();
        for (String key : keys) {
            Material m = tryResolveMaterial(key);
            if (m != null) mats.add(m);
        }
        if (!mats.isEmpty()) {
            Material icon = tryResolveMaterial(iconKey);
            if (icon == null) icon = Material.PAPER;
            CATEGORIES.put(name, new CategoryDef(icon, mats.toArray(new Material[0])));
        }
    }

    private static class CategoryDef {
        final Material icon;
        final List<Material> materials;
        CategoryDef(Material icon, Material... mats) {
            this.icon = icon;
            this.materials = Arrays.asList(mats);
        }
    }

    // ── Instance state ────────────────────────────────────────────────────────
    private final PlayerStallCraft plugin;
    private final Player player;
    private final Consumer<ItemStack> callback;
    private Inventory inventory;

    private String currentCategory = null; // null = category home view
    private List<Material> currentItems = new ArrayList<>();
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28;
    private String searchFilter = "";
    private boolean waitingForSearch = false;
    private boolean callbackFired = false;

    public ItemSelectGUI(PlayerStallCraft plugin, Player player, Consumer<ItemStack> callback) {
        this.plugin = plugin;
        this.player = player;
        this.callback = callback;
        this.inventory = Bukkit.createInventory(null, 54, "§a§l选择物品 §7— 分类");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        fillInventory();
    }

    // ── Fill ──────────────────────────────────────────────────────────────────
    private void fillInventory() {
        inventory.clear();
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, border);
            inventory.setItem(i + 8, border);
        }

        if (currentCategory == null && searchFilter.isEmpty()) {
            fillCategoryHome();
        } else {
            fillItemGrid();
        }
    }

    private void fillCategoryHome() {
        List<String> keys = new ArrayList<>(CATEGORIES.keySet());
        int slot = 10;
        for (String key : keys) {
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            CategoryDef def = CATEGORIES.get(key);
            inventory.setItem(slot, createCategoryItem(def.icon, key, def.materials.size()));
            slot++;
        }
        // Search across all
        inventory.setItem(49, createItem(Material.COMPASS, "§e搜索所有物品",
                "", "§7点击输入关键词全局搜索"));
        inventory.setItem(53, createItem(Material.BARRIER, "§c取消"));
    }

    private void fillItemGrid() {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int slot = 10;
        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, currentItems.size()); i++) {
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            inventory.setItem(slot, createMaterialItem(currentItems.get(i)));
            slot++;
        }
        // Back to categories
        inventory.setItem(45, createItem(Material.ARROW, "§7返回分类"));
        if (currentPage > 0)
            inventory.setItem(47, createItem(Material.ARROW, "§a上一页"));
        // Search bar
        String searchLabel = searchFilter.isEmpty()
                ? "§e搜索" + (currentCategory != null ? "[" + currentCategory + "]" : "")
                : "§e搜索: §f" + searchFilter;
        inventory.setItem(49, createItem(Material.COMPASS, searchLabel,
                "", "§7点击输入关键词", "§c右键清除搜索"));
        int maxPage = Math.max(0, (currentItems.size() - 1) / ITEMS_PER_PAGE);
        if (currentPage < maxPage)
            inventory.setItem(51, createItem(Material.ARROW, "§a下一页"));
        inventory.setItem(53, createItem(Material.BARRIER, "§c取消"));
    }

    // ── Item creation ─────────────────────────────────────────────────────────
    private ItemStack createCategoryItem(Material icon, String name, int count) {
        ItemStack item = new ItemStack(icon);
        try { item = new ItemStack(icon); } catch (Exception ignored) { item = new ItemStack(Material.CHEST); }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l" + name);
            meta.setLore(Arrays.asList("", "§7包含 §f" + count + " §7种物品", "", "§a点击进入"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMaterialItem(Material mat) {
        String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(mat);
        String displayName = zhName != null ? zhName : mat.name().toLowerCase().replace("_", " ");
        List<String> lore = Arrays.asList("§8" + mat.name(), "", "§a点击选择此物品");
        try {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + displayName);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        } catch (Exception ignored) {}
        ItemStack fallback = new ItemStack(Material.PAPER);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + displayName);
            meta.setLore(lore);
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private void openCategory(String category) {
        currentCategory = category;
        searchFilter = "";
        currentPage = 0;
        currentItems = new ArrayList<>(CATEGORIES.get(category).materials);
        refreshInventory("§a§l" + category);
    }

    private void backToCategories() {
        currentCategory = null;
        searchFilter = "";
        currentPage = 0;
        currentItems = new ArrayList<>();
        refreshInventory("§a§l选择物品 §7— 分类");
    }

    private void refreshInventory(String title) {
        inventory = Bukkit.createInventory(null, 54, title);
        fillInventory();
        player.openInventory(inventory);
    }

    public void open() {
        player.openInventory(inventory);
    }

    // ── Click handler ─────────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Cancel / close
        if (slot == 53) {
            HandlerList.unregisterAll(this);
            player.closeInventory();
            callback.accept(null);
            return;
        }

        // Category home view
        if (currentCategory == null && searchFilter.isEmpty()) {
            if (slot == 49) {
                // Global search
                doSearchPrompt(null);
                return;
            }
            if (isItemSlot(slot)) {
                List<String> keys = new ArrayList<>(CATEGORIES.keySet());
                int row = slot / 9 - 1;
                int col = slot % 9 - 1;
                int index = row * 7 + col;
                if (index >= 0 && index < keys.size()) {
                    openCategory(keys.get(index));
                }
            }
            return;
        }

        // Item grid view
        if (slot == 45) { backToCategories(); return; }
        if (slot == 47 && currentPage > 0) { currentPage--; refreshInventory(currentCategory != null ? "§a§l" + currentCategory : "§e搜索结果"); return; }
        if (slot == 49) {
            if (event.isRightClick()) {
                searchFilter = "";
                currentItems = new ArrayList<>(currentCategory != null ? CATEGORIES.get(currentCategory).materials : new ArrayList<>());
                currentPage = 0;
                refreshInventory(currentCategory != null ? "§a§l" + currentCategory : "§a§l选择物品 §7— 分类");
            } else {
                doSearchPrompt(currentCategory);
            }
            return;
        }
        if (slot == 51) {
            int maxPage = Math.max(0, (currentItems.size() - 1) / ITEMS_PER_PAGE);
            if (currentPage < maxPage) { currentPage++; refreshInventory(currentCategory != null ? "§a§l" + currentCategory : "§e搜索结果"); }
            return;
        }

        // Select item
        if (isItemSlot(slot)) {
            int row = slot / 9 - 1;
            int col = slot % 9 - 1;
            int index = currentPage * ITEMS_PER_PAGE + row * 7 + col;
            if (index >= 0 && index < currentItems.size()) {
                ItemStack selected = new ItemStack(currentItems.get(index), 1);
                callbackFired = true;
                HandlerList.unregisterAll(this);
                player.closeInventory();
                callback.accept(selected);
            }
        }
    }

    private void doSearchPrompt(String scopeCategory) {
        waitingForSearch = true;
        player.closeInventory();
        String scope = scopeCategory != null ? "【" + scopeCategory + "】" : "「全物品」";
        plugin.getMessageManager().sendRaw(player, "&e请输入关键词在" + scope + "中搜索，输入 cancel 取消:");
    }

    private boolean isItemSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= 1 && row <= 4 && col >= 1 && col <= 7;
    }

    // ── Chat input ────────────────────────────────────────────────────────────
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForSearch) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();
        waitingForSearch = false;

        if (message.equalsIgnoreCase("cancel") || message.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                refreshInventory(currentCategory != null ? "§a§l" + currentCategory : "§a§l选择物品 §7— 分类"));
            return;
        }

        searchFilter = message.toLowerCase();
        final String filter = searchFilter.replace(" ", "_");
        List<Material> pool = currentCategory != null
                ? CATEGORIES.get(currentCategory).materials
                : Arrays.stream(Material.values()).filter(m -> m.isItem() && !m.isAir()).collect(Collectors.toList());
        currentItems = pool.stream()
                .filter(m -> m.name().toLowerCase().contains(filter) ||
                        (plugin.getGlobalMarketManager().getChineseNamePublic(m) != null &&
                         plugin.getGlobalMarketManager().getChineseNamePublic(m).contains(message.toLowerCase())))
                .collect(Collectors.toList());
        currentPage = 0;
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshInventory("§e搜索: " + message));
    }

    // ── Inventory close ───────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForSearch && !callbackFired) {
            HandlerList.unregisterAll(this);
            callback.accept(null);
        }
    }
}
