package dev.rptag.vampire;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VampireItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Vampire.MODID);

    /** Garrafa vazia: 7 vidros (receita). A mordida do vampiro a enche. */
    public static final DeferredItem<Item> EMPTY_FLASK = ITEMS.register("blood_flask_empty",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BloodFlaskItem> BLOOD_FLASK = ITEMS.register("blood_flask",
            () -> new BloodFlaskItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BatAmuletItem> BAT_AMULET = ITEMS.register("bat_amulet",
            () -> new BatAmuletItem(new Item.Properties().stacksTo(1)));

    private VampireItems() {}
}
