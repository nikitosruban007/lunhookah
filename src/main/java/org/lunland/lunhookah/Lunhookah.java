package org.lunland.lunhookah;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Lunhookah extends JavaPlugin implements Listener {
    private Map<UUID, Long> cooldownMap = new HashMap<>();
    private NamespacedKey hookahKey;
    private NamespacedKey takenKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hookahKey = new NamespacedKey(this, "hookah");
        takenKey = new NamespacedKey(this, "hookah_taken");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCustomBrewingStandRecipe();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasMetadata("hookahStand")) {
                    Location hookahLocation = (Location) player.getMetadata("hookahStand").get(0).value();
                    if (!player.getWorld().equals(hookahLocation.getWorld())) {
                        updateStandAvailability(hookahLocation);
                        player.removeMetadata("hookahStand", this);
                        continue;
                    }
                    if (player.getLocation().distance(hookahLocation) > 7) {
                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item != null && item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                                    getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
                                player.getInventory().remove(item);
                            }
                        }
                        updateStandAvailability(hookahLocation);
                        player.removeMetadata("hookahStand", this);
                    }
                }
            }
        }, 20L, 20L);
    }

    private void registerCustomBrewingStandRecipe() {
        ItemStack hookahBrewingStand = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = hookahBrewingStand.getItemMeta();
        meta.setDisplayName(getConfig().getString("brewingStandDisplayName", "Кальян"));
        hookahBrewingStand.setItemMeta(meta);
        ShapedRecipe recipe = null;
        ConfigurationSection recipeSection = getConfig().getConfigurationSection("recipe.brewingStand");
        if (recipeSection != null) {
            List<String> shape = recipeSection.getStringList("shape");
            if (shape.size() != 3) {
                getLogger().warning("Неверное число строк в рецепте. Используются значения по умолчанию.");
                shape = Arrays.asList(" B ", " S ", " B ");
            }
            recipe = new ShapedRecipe(new NamespacedKey(this, "hookah_brewing_stand"), hookahBrewingStand);
            recipe.shape(shape.get(0), shape.get(1), shape.get(2));
            ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
            if (ingredientsSection != null) {
                for (String key : ingredientsSection.getKeys(false)) {
                    String materialStr = ingredientsSection.getString(key);
                    try {
                        Material material = Material.valueOf(materialStr);
                        recipe.setIngredient(key.charAt(0), material);
                    } catch (Exception e) {
                        getLogger().warning("Неверный материал для ингредиента '" + key + "': " + materialStr);
                    }
                }
            }
        } else {
            recipe = new ShapedRecipe(new NamespacedKey(this, "hookah_brewing_stand"), hookahBrewingStand);
            recipe.shape(" B ", " S ", " B ");
            recipe.setIngredient('B', Material.BAMBOO);
            recipe.setIngredient('S', Material.BREWING_STAND);
        }
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onHookahBambooPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta()) {
            String displayName = item.getItemMeta().getDisplayName();
            Block block = event.getBlockPlaced();
            if (getConfig().getString("brewingStandDisplayName", "Кальян").equals(displayName) && block.getType() == Material.BREWING_STAND) {
                BlockState state = block.getState();
                if (state instanceof BrewingStand brewingStand) {
                    brewingStand.getPersistentDataContainer().set(hookahKey, PersistentDataType.STRING, "true");
                    brewingStand.getPersistentDataContainer().set(takenKey, PersistentDataType.BYTE, (byte) 0);
                    brewingStand.update();
                }
            }
        }
    }

    @EventHandler
    public void onBrewingStandRightClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.BREWING_STAND) {
                BlockState state = block.getState();
                if (state instanceof BrewingStand brewingStand) {
                    if (brewingStand.getPersistentDataContainer().has(hookahKey, PersistentDataType.STRING)) {
                        event.setCancelled(true);
                        Byte taken = brewingStand.getPersistentDataContainer().get(takenKey, PersistentDataType.BYTE);
                        if (taken != null && taken == (byte) 1) {
                            sendActionBar(event.getPlayer(), getConfig().getString("message.alreadyTaken", "С этой стойки уже взят кальянный мундштук."));
                            return;
                        }
                        Player player = event.getPlayer();
                        for (ItemStack invItem : player.getInventory().getContents()) {
                            if (invItem != null && invItem.getType() == Material.BAMBOO && invItem.hasItemMeta() &&
                                    getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(invItem.getItemMeta().getDisplayName())) {
                                sendActionBar(player, getConfig().getString("message.alreadyHaveBamboo", "У вас уже есть кальянный мундштук."));
                                return;
                            }
                        }
                        ItemStack bamboo = new ItemStack(Material.BAMBOO);
                        ItemMeta meta = bamboo.getItemMeta();
                        meta.setDisplayName(getConfig().getString("bambooDisplayName", "Кальянный Мундштук"));
                        bamboo.setItemMeta(meta);
                        player.getInventory().addItem(bamboo);
                        sendActionBar(player, getConfig().getString("message.giveBamboo", "Вы получили кальянный мундштук. Используйте его, чтобы курить."));
                        player.setMetadata("hookahStand", new FixedMetadataValue(this, block.getLocation()));
                        brewingStand.getPersistentDataContainer().set(takenKey, PersistentDataType.BYTE, (byte) 1);
                        brewingStand.update();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBambooUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                UUID uuid = player.getUniqueId();
                long currentTime = System.currentTimeMillis();
                if (cooldownMap.containsKey(uuid)) {
                    long lastUse = cooldownMap.get(uuid);
                    if (currentTime - lastUse < 3000) {
                        sendActionBar(player, getConfig().getString("message.wait", "Подождите, прежде чем сделать следующую затяжку."));
                        return;
                    }
                }
                cooldownMap.put(uuid, currentTime);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 3));
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0),
                        27, 0.675, 0.675, 0.675, 0.0);
                sendActionBar(player, getConfig().getString("message.puff", "Вы сделали затяжку кальяна."));
            }
        }
    }

    @EventHandler
    public void onBambooDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType() == Material.BAMBOO && dropped.hasItemMeta() &&
                getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(dropped.getItemMeta().getDisplayName())) {
            event.getItemDrop().remove();
            Player player = event.getPlayer();
            if (player.hasMetadata("hookahStand")) {
                Location hookahLocation = (Location) player.getMetadata("hookahStand").get(0).value();
                updateStandAvailability(hookahLocation);
                player.removeMetadata("hookahStand", this);
            }
            sendActionBar(player, getConfig().getString("message.bambooDropped", "Кальянный мундштук возвращен в кальян."));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                    getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
                player.getInventory().remove(item);
            }
        }
        cooldownMap.remove(player.getUniqueId());
        if (player.hasMetadata("hookahStand")) {
            player.removeMetadata("hookahStand", this);
        }
    }

    @EventHandler
    public void onBrewingStandBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.BREWING_STAND) {
            BlockState state = block.getState();
            if (state instanceof BrewingStand brewingStand) {
                if (brewingStand.getPersistentDataContainer().has(hookahKey, PersistentDataType.STRING)) {
                    Location brokenLocation = block.getLocation();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasMetadata("hookahStand")) {
                            Location hookahLocation = (Location) player.getMetadata("hookahStand").get(0).value();
                            if (hookahLocation.equals(brokenLocation)) {
                                for (ItemStack item : player.getInventory().getContents()) {
                                    if (item != null && item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                                            getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
                                        player.getInventory().remove(item);
                                    }
                                }
                                player.removeMetadata("hookahStand", this);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateStandAvailability(Location location) {
        if (location != null && location.getBlock().getType() == Material.BREWING_STAND) {
            Block block = location.getBlock();
            BlockState state = block.getState();
            if (state instanceof BrewingStand brewingStand) {
                brewingStand.getPersistentDataContainer().set(takenKey, PersistentDataType.BYTE, (byte) 0);
                brewingStand.update();
            }
        }
    }

    public void sendActionBar(Player player, String message) {
        Component component = MiniMessage.miniMessage().deserialize(message);
        ((Audience) player).sendActionBar(component);
    }


    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.BAMBOO && item.hasItemMeta() &&
                        getConfig().getString("bambooDisplayName", "Кальянный Мундштук").equals(item.getItemMeta().getDisplayName())) {
                    player.getInventory().remove(item);
                }
            }
            cooldownMap.remove(player.getUniqueId());
            if (player.hasMetadata("hookahStand")) {
                player.removeMetadata("hookahStand", this);
            }
        }
    }
}
