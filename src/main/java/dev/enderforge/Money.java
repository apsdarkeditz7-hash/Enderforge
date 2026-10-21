package dev.enderforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.*;

class MoneyMaker extends EfModule {
    MoneyMaker(Enderforge pl) { super(pl, "money-maker", 13); }
    @Override public List<String> commands() { return List.of("balance"); }

    @Override
    protected void onEnable() {
        int secs = Math.max(1, cfgInt("playtime-seconds", 60));
        every(secs * 20L, () -> {
            double r = cfgDouble("playtime-reward", 2.0);
            for (Player p : Bukkit.getOnlinePlayers()) pay(p, r);
        });
    }

    @Override
    public boolean onCommand(Player p, String[] a) {
        msg(p, String.format("Balance: $%.2f", plugin.data().balance(p.getUniqueId())), NamedTextColor.GOLD);
        return true;
    }

    private void pay(Player p, double amt) {
        if (amt <= 0) return;
        plugin.data().addBalance(p.getUniqueId(), amt);
        p.sendActionBar(Component.text(String.format("+$%.2f", amt), NamedTextColor.GOLD));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        // Silent cooldown: block/kill payouts happen at most once per cooldown-seconds.
        if (!tryCooldown(p, "money-maker", cooldownSeconds())) return;
        boolean ore = e.getBlock().getType().name().endsWith("_ORE") || e.getBlock().getType() == Material.ANCIENT_DEBRIS;
        pay(p, ore ? cfgDouble("ore-reward", 5.0) : cfgDouble("block-reward", 1.0));
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        if (!tryCooldown(k, "money-maker", cooldownSeconds())) return;
        pay(k, cfgDouble("mob-reward", 5.0));
    }
}

class InfinityGoldMaker extends EfModule {
    private final Set<UUID> active = new HashSet<>();
    InfinityGoldMaker(Enderforge pl) { super(pl, "infinity-gold-maker", 20); }
    @Override public List<String> commands() { return List.of("goldmaker"); }

    @Override
    protected void onEnable() {
        every(Math.max(1, cooldownSeconds()) * 20L, () -> {
            int amount = cfgInt("amount", 8);
            for (UUID id : new ArrayList<>(active)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) { active.remove(id); continue; }
                give(p, new ItemStack(Material.GOLD_INGOT, amount));
            }
        });
    }

    @Override protected void onDisable() { active.clear(); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        if (!active.remove(p.getUniqueId())) {
            active.add(p.getUniqueId());
            msg(p, "Infinity Gold Maker ON: gold every " + cooldownSeconds() + "s.", NamedTextColor.GOLD);
        } else msg(p, "Infinity Gold Maker OFF.");
        return true;
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }
}

class HeartIncreaser extends EfModule {
    HeartIncreaser(Enderforge pl) { super(pl, "heart-increaser", 20); }
    @Override public List<String> commands() { return List.of("hearts"); }
    @Override public List<String> tab(String[] a) { return a.length == 1 ? List.of("buy", "info") : List.of(); }

    private void apply(Player p, boolean on) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(20.0 + (on ? 2.0 * plugin.data().extraHearts(p.getUniqueId()) : 0));
    }

    @Override protected void onEnable() { Bukkit.getOnlinePlayers().forEach(p -> apply(p, true)); }
    @Override protected void onDisable() { Bukkit.getOnlinePlayers().forEach(p -> apply(p, false)); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { apply(e.getPlayer(), true); }

    private double cost(Player p) {
        return cfgDouble("base-cost", 500.0) * (plugin.data().extraHearts(p.getUniqueId()) + 1);
    }

    @Override
    public boolean onCommand(Player p, String[] a) {
        var id = p.getUniqueId();
        int extra = plugin.data().extraHearts(id), max = cfgInt("max-extra-hearts", 10);
        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            msg(p, "Extra hearts: " + extra + "/" + max + (extra < max ? String.format("  Next: $%.0f  (/hearts buy)", cost(p)) : "  (max)"));
            return true;
        }
        if (!a[0].equalsIgnoreCase("buy")) { msg(p, "Usage: /hearts <buy|info>"); return true; }
        if (extra >= max) { msg(p, "You already have the maximum extra hearts.", NamedTextColor.RED); return true; }
        double c = cost(p);
        if (plugin.data().balance(id) < c) { msg(p, String.format("Not enough money. Need $%.0f.", c), NamedTextColor.RED); return true; }
        if (!ready(p)) return true;
        plugin.data().addBalance(id, -c);
        plugin.data().setExtraHearts(id, extra + 1);
        apply(p, true);
        PowerModule.heal(p, 2);
        msg(p, "+1 heart! You now have " + (extra + 1) + " extra.", NamedTextColor.RED);
        return true;
    }
}
