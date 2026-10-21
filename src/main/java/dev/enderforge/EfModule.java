package dev.enderforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** Base class for all 15 Enderforge modules. */
public abstract class EfModule implements Listener {
    protected final Enderforge plugin;
    private final String id;
    private final int defaultCooldown;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean enabled;

    protected EfModule(Enderforge plugin, String id, int defaultCooldown) {
        this.plugin = plugin;
        this.id = id;
        this.defaultCooldown = defaultCooldown;
    }

    public String id() { return id; }
    public boolean isEnabled() { return enabled; }
    public int cooldownSeconds() { return plugin.getConfig().getInt(path("cooldown-seconds"), defaultCooldown); }

    protected String path(String key) { return "modules." + id + "." + key; }
    protected int cfgInt(String key, int def) { return plugin.getConfig().getInt(path(key), def); }
    protected double cfgDouble(String key, double def) { return plugin.getConfig().getDouble(path(key), def); }

    /** Command names (from plugin.yml) this module handles. */
    public List<String> commands() { return List.of(); }
    public boolean onCommand(Player p, String[] args) { return false; }
    public List<String> tab(String[] args) { return List.of(); }

    protected void onEnable() {}
    protected void onDisable() {}

    public final void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        onEnable();
    }

    public final void disable() {
        if (!enabled) return;
        enabled = false;
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        onDisable();
    }

    protected void every(long ticks, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, r, ticks, ticks));
    }

    /** Silent cooldown check (starts the cooldown when ready). */
    protected boolean tryCooldown(Player p, String key, int seconds) {
        if (p.hasPermission("enderforge.bypass")) return true;
        return plugin.cooldowns().tryUse(p.getUniqueId(), key, seconds) == 0;
    }

    /** Cooldown check that tells the player how long is left. */
    protected boolean ready(Player p) {
        if (p.hasPermission("enderforge.bypass")) return true;
        long left = plugin.cooldowns().tryUse(p.getUniqueId(), id, cooldownSeconds());
        if (left > 0) {
            p.sendActionBar(Component.text(id + " cooldown: " + left + "s", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    protected void msg(Player p, String text) { msg(p, text, NamedTextColor.LIGHT_PURPLE); }
    protected void msg(Player p, String text, NamedTextColor c) { p.sendMessage(Component.text(text, c)); }

    protected void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }
}
