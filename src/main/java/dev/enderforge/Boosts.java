package dev.enderforge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

class XpBooster extends EfModule {
    private final Set<UUID> active = new HashSet<>();
    XpBooster(Enderforge pl) { super(pl, "xp-booster", 13); }
    @Override public List<String> commands() { return List.of("xpboost"); }
    @Override protected void onDisable() { active.clear(); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        if (!ready(p)) return true;
        if (!active.remove(p.getUniqueId())) {
            active.add(p.getUniqueId());
            msg(p, "XP Booster ON (x" + cfgDouble("multiplier", 2.0) + ").");
        } else msg(p, "XP Booster OFF.");
        return true;
    }

    @EventHandler
    public void onXp(PlayerExpChangeEvent e) {
        if (active.contains(e.getPlayer().getUniqueId()) && e.getAmount() > 0)
            e.setAmount((int) Math.ceil(e.getAmount() * cfgDouble("multiplier", 2.0)));
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }
}

class FlyModule extends EfModule {
    private final Set<UUID> creative = new HashSet<>(), dash = new HashSet<>();
    private final Map<UUID, BukkitTask> timed = new HashMap<>();
    private static final List<String> MODES = List.of("creative", "timed", "dash", "boost", "glide");

    FlyModule(Enderforge pl) { super(pl, "fly-module", 10); }
    @Override public List<String> commands() { return List.of("fly"); }
    @Override public List<String> tab(String[] a) { return a.length == 1 ? MODES : List.of(); }

    private void refresh(Player p) {
        UUID id = p.getUniqueId();
        GameMode g = p.getGameMode();
        boolean allow = creative.contains(id) || dash.contains(id) || timed.containsKey(id)
                || g == GameMode.CREATIVE || g == GameMode.SPECTATOR;
        p.setAllowFlight(allow);
        if (!allow) p.setFlying(false);
    }

    @Override
    protected void onDisable() {
        Set<UUID> all = new HashSet<>(creative);
        all.addAll(dash);
        all.addAll(timed.keySet());
        timed.values().forEach(BukkitTask::cancel);
        creative.clear(); dash.clear(); timed.clear();
        for (UUID id : all) { Player p = Bukkit.getPlayer(id); if (p != null) refresh(p); }
    }

    @Override
    public boolean onCommand(Player p, String[] a) {
        String mode = a.length == 0 ? "creative" : a[0].toLowerCase();
        if (!MODES.contains(mode)) { msg(p, "Usage: /fly <creative|timed|dash|boost|glide>"); return true; }
        if (!ready(p)) return true;
        UUID id = p.getUniqueId();
        switch (mode) {
            case "creative" -> {
                if (creative.remove(id)) { refresh(p); msg(p, "Creative flight OFF."); }
                else { creative.add(id); refresh(p); msg(p, "Creative flight ON."); }
            }
            case "dash" -> {
                if (dash.remove(id)) { refresh(p); msg(p, "Double-jump dash OFF."); }
                else { dash.add(id); refresh(p); msg(p, "Double-jump dash ON: jump twice to dash."); }
            }
            case "timed" -> {
                BukkitTask old = timed.remove(id);
                if (old != null) { old.cancel(); refresh(p); msg(p, "Timed flight ended."); return true; }
                int secs = cfgInt("timed-seconds", 60);
                p.setAllowFlight(true);
                timed.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    timed.remove(id);
                    Player q = Bukkit.getPlayer(id);
                    if (q == null) return;
                    refresh(q);
                    q.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                    msg(q, "Timed flight ended.");
                }, secs * 20L));
                msg(p, "Timed flight for " + secs + "s.");
            }
            case "boost" -> {
                if (!p.isGliding()) { msg(p, "Boost works while gliding with an elytra."); return true; }
                p.setVelocity(p.getVelocity().add(p.getLocation().getDirection().multiply(1.5)));
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 30, 0.3, 0.3, 0.3, 0.1);
            }
            default -> {
                int secs = cfgInt("glide-seconds", 30);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, secs * 20, 0));
                msg(p, "Gliding for " + secs + "s.");
            }
        }
        return true;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!e.isFlying() || !dash.contains(id) || creative.contains(id) || timed.containsKey(id)) return;
        GameMode g = p.getGameMode();
        if (g == GameMode.CREATIVE || g == GameMode.SPECTATOR) return;
        e.setCancelled(true);
        if (!tryCooldown(p, "fly-dash", cfgInt("dash-cooldown-seconds", 3))) return;
        Vector v = p.getLocation().getDirection().multiply(1.2);
        v.setY(Math.max(0.5, v.getY() + 0.4));
        p.setVelocity(v);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        creative.remove(id); dash.remove(id);
        BukkitTask t = timed.remove(id);
        if (t != null) t.cancel();
        refresh(p);
    }
}

class Smelter extends EfModule {
    private static final Map<Material, Material> MAP = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT), Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT), Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP),
            Map.entry(Material.COBBLESTONE, Material.STONE), Map.entry(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE),
            Map.entry(Material.SAND, Material.GLASS), Map.entry(Material.RED_SAND, Material.GLASS),
            Map.entry(Material.CLAY_BALL, Material.BRICK), Map.entry(Material.NETHERRACK, Material.NETHER_BRICK),
            Map.entry(Material.BEEF, Material.COOKED_BEEF), Map.entry(Material.PORKCHOP, Material.COOKED_PORKCHOP),
            Map.entry(Material.CHICKEN, Material.COOKED_CHICKEN), Map.entry(Material.MUTTON, Material.COOKED_MUTTON),
            Map.entry(Material.RABBIT, Material.COOKED_RABBIT), Map.entry(Material.COD, Material.COOKED_COD),
            Map.entry(Material.SALMON, Material.COOKED_SALMON), Map.entry(Material.POTATO, Material.BAKED_POTATO),
            Map.entry(Material.KELP, Material.DRIED_KELP));

    private final Set<UUID> auto = new HashSet<>();
    Smelter(Enderforge pl) { super(pl, "smelter", 13); }
    @Override public List<String> commands() { return List.of("smelter"); }
    @Override public List<String> tab(String[] a) { return a.length == 1 ? List.of("inventory", "auto") : List.of(); }
    @Override protected void onDisable() { auto.clear(); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        String sub = a.length == 0 ? "inventory" : a[0].toLowerCase();
        if (!sub.equals("inventory") && !sub.equals("auto")) { msg(p, "Usage: /smelter <inventory|auto>"); return true; }
        if (!ready(p)) return true;
        if (sub.equals("auto")) {
            if (!auto.remove(p.getUniqueId())) { auto.add(p.getUniqueId()); msg(p, "Auto-smelt drops ON."); }
            else msg(p, "Auto-smelt drops OFF.");
            return true;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        int n = 0;
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null) continue;
            Material out = MAP.get(c[i].getType());
            if (out != null) { c[i] = new ItemStack(out, c[i].getAmount()); n++; }
        }
        inv.setContents(c);
        msg(p, "Smelted " + n + " stacks.");
        return true;
    }

    @EventHandler
    public void onBlockDrops(BlockDropItemEvent e) {
        if (!auto.contains(e.getPlayer().getUniqueId())) return;
        for (Item item : e.getItems()) {
            ItemStack s = item.getItemStack();
            Material out = MAP.get(s.getType());
            if (out != null) item.setItemStack(new ItemStack(out, s.getAmount()));
        }
    }

    @EventHandler
    public void onMobDrops(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || !auto.contains(k.getUniqueId())) return;
        e.getDrops().replaceAll(s -> MAP.containsKey(s.getType()) ? new ItemStack(MAP.get(s.getType()), s.getAmount()) : s);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { auto.remove(e.getPlayer().getUniqueId()); }
}
