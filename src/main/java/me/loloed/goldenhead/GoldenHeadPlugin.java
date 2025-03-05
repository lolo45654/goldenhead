package me.loloed.goldenhead;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public final class GoldenHeadPlugin extends JavaPlugin implements Listener {
    private static final SimpleCommandExceptionType PLAYER_REQUIRED = new SimpleCommandExceptionType(new LiteralMessage("Requires player"));
    private static final NamespacedKey LAST_USE_KEY = new NamespacedKey("golden_head", "last_golden_head_use");
    private static final long COOLDOWN_MILLIS = 15000L;
    public static final NamespacedKey ITEM_ID = new NamespacedKey("golden_head", "is_item");
    public static final ItemStack ITEM;

    static {
        ITEM = new ItemStack(Material.PLAYER_HEAD);
        ITEM.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                .uuid(UUID.randomUUID())
                .addProperty(new ProfileProperty("textures", "ewogICJ0aW1lc3RhbXAiIDogMTcyNjIzMzgyODUxMiwKICAicHJvZmlsZUlkIiA6ICJhYzY1NDYwOWVkZjM0ODhmOTM0ZWNhMDRmNjlkNGIwMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGFjZUd1cmxTa3kiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmUxNTUxYjk0MzRmYWUxZThlOTMyN2Y3YzFjZWFjN2JjYWUzY2Y3MWZiZDY3ZTc5NDJmMGMyY2U2MjgwNWMxOCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9")).build());
        ITEM.setData(DataComponentTypes.ITEM_NAME, Component.text("Golden Head"));
        ITEM.setData(DataComponentTypes.RARITY, ItemRarity.RARE);
        ITEM.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .sound(Key.key("entity.generic.eat"))
                .addEffect(ConsumeEffect
                        .applyStatusEffects(List.of(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 2, false),
                                new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0, false)), 1)).build());
        ITEM.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(4)
                .saturation(9.6f)
                .canAlwaysEat(true).build());
        ItemMeta meta = ITEM.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.BOOLEAN, true);
        ITEM.setItemMeta(meta);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("givegoldenhead")
                    .requires(source -> source.getSender().hasPermission("goldenhead.give"))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> {
                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                Entity executor = ctx.getSource().getExecutor();
                                if (!(executor instanceof Player player)) throw PLAYER_REQUIRED.create();
                                player.give(ITEM.asQuantity(count));
                                ctx.getSource().getSender().sendMessage("Gave " + player.getName() + " " + count + " golden head(s).");
                                return 1;
                            }))
                    .executes(ctx -> {
                        Entity executor = ctx.getSource().getExecutor();
                        if (!(executor instanceof Player player)) throw PLAYER_REQUIRED.create();
                        player.give(ITEM);
                        ctx.getSource().getSender().sendMessage("Gave " + player.getName() + " a golden head.");
                        return 1;
                    })
                    .build());
        });
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getPersistentDataContainer().get(ITEM_ID, PersistentDataType.BOOLEAN) != Boolean.TRUE) {
            return;
        }

        Player player = event.getPlayer();
        Long lastUse = player.getPersistentDataContainer().get(LAST_USE_KEY, PersistentDataType.LONG);
        if (lastUse != null && System.currentTimeMillis() < lastUse + COOLDOWN_MILLIS) {
            return;
        }
        event.setCancelled(true);

        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        net.minecraft.world.item.component.Consumable consumable = nmsItem.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            net.minecraft.world.item.ItemStack newStack = consumable.onConsume(((CraftWorld) player.getWorld()).getHandle(), ((CraftPlayer) player).getHandle(), nmsItem);
            player.getInventory().setItem(event.getHand(), newStack.getBukkitStack());
        }
        player.swingHand(event.getHand());
        player.getPersistentDataContainer().set(LAST_USE_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getPersistentDataContainer().get(ITEM_ID, PersistentDataType.BOOLEAN) != Boolean.TRUE) {
            return;
        }
        event.setCancelled(true);
    }
}
