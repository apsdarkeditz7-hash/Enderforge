package dev.enderforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** A power with 3 phases. Each use casts the current phase, then advances to the next. */
abstract class PowerModule extends EfModule {
    private final String title;
    private final String[] phaseNames;
    private final Map<UUID, Integer> phase = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();

    PowerModule(Enderforge plugin, String id, String title, int cd, String... phaseNames) {
        super(plugin, id, cd);
        this.title = title;
        this.phaseNames = phaseNames;
    }

    abstract void cast(Player p, int phase);

    void use(Player p) {
        if (!isEnabled()) { msg(p, title + " is disabled.", NamedTextColor.RED); return; }
        if (!ready(p)) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        int resetMs = plugin.getConfig().getInt("modules.elemental-switch.phase-reset-seconds", 30) * 1000;
        int ph = phase.getOrDefault(id, 1);
        if (now - last.getOrDefault(id, 0L) > resetMs) ph = 1;
        cast(p, ph);
        p.sendActionBar(Component.text(title + " - Phase " + ph + ": " + phaseNames[ph - 1], NamedTextColor.GOLD));
        phase.put(id, ph % 3 + 1);
        last.put(id, now);
    }

    protected List<LivingEntity> near(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r))
            if (e instanceof LivingEntity le && !(e instanceof ArmorStand)) out.add(le);
        return out;
    }

    static void heal(Player t, double amt) {
        double max = t.getAttribute(Attribute.MAX_HEALTH).getValue();
        t.setHealth(Math.min(max, t.getHealth() + amt));
    }

    protected void push(LivingEntity t, Location from, double power, double up) {
        Vector v = t.getLocation().toVector().subtract(from.toVector());
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 0); else v.normalize();
        t.setVelocity(v.multiply(power).setY(up));
    }
}

class GrassPower extends PowerModule {
    GrassPower(Enderforge pl) { super(pl, "grass-power", "Grass Power", 10, "Vine Whip", "Root Trap", "Healing Bloom"); }

    @Override
    void cast(Player p, int ph) {
        World w = p.getWorld();
        switch (ph) {
            case 1 -> {
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                RayTraceResult r = w.rayTraceEntities(eye, dir, 10, 0.5, e -> e instanceof LivingEntity && !e.equals(p));
                double dist = 10;
                if (r != null && r.getHitEntity() instanceof LivingEntity t) {
                    dist = eye.distance(t.getLocation());
                    t.damage(4, p);
                    Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                    if (pull.lengthSquared() > 0.0001) t.setVelocity(pull.normalize().multiply(1.1).setY(0.3));
                }
                for (double d = 1; d < dist; d += 0.5)
                    w.spawnParticle(Particle.HAPPY_VILLAGER, eye.clone().add(dir.clone().multiply(d)), 2, 0.1, 0.1, 0.1, 0);
                w.playSound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 0.8f);
            }
            case 2 -> {
                for (LivingEntity t : near(p, 6)) {
                    t.damage(2, p);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
                    w.spawnParticle(Particle.HAPPY_VILLAGER, t.getLocation(), 25, 0.4, 0.2, 0.4, 0);
                }
                w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 60, 3, 0.2, 3, 0);
                w.playSound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 0.5f);
            }
            default -> {
                List<Player> group = new ArrayList<>();
                group.add(p);
                for (Entity e : p.getNearbyEntities(6, 6, 6)) if (e instanceof Player o) group.add(o);
                for (Player t : group) {
                    heal(t, 8);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                    w.spawnParticle(Particle.HAPPY_VILLAGER, t.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            }
        }
    }
}

class EarthquakePower extends PowerModule {
    EarthquakePower(Enderforge pl) { super(pl, "earthquake-power", "Earthquake Power", 20, "Ground Shake", "Fissure", "Quake Slam"); }

    @Override
    void cast(Player p, int ph) {
        World w = p.getWorld();
        var dirt = Material.DIRT.createBlockData();
        switch (ph) {
            case 1 -> {
                for (LivingEntity t : near(p, 5)) { t.damage(3, p); push(t, p.getLocation(), 0.4, 0.4); }
                w.spawnParticle(Particle.BLOCK, p.getLocation(), 60, 2.5, 0.3, 2.5, 0.1, dirt);
                w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
            }
            case 2 -> {
                Vector d = p.getLocation().getDirection().setY(0);
                if (d.lengthSquared() < 0.0001) d = new Vector(0, 0, 1);
                d.normalize();
                for (int i = 1; i <= 12; i++) {
                    Location l = p.getLocation().add(d.clone().multiply(i));
                    w.spawnParticle(Particle.BLOCK, l, 20, 0.4, 0.2, 0.4, 0.1, dirt);
                    for (Entity e : w.getNearbyEntities(l, 1.5, 2, 1.5))
                        if (e instanceof LivingEntity t && !e.equals(p) && !(e instanceof ArmorStand)) {
                            t.damage(4, p);
                            t.setVelocity(new Vector(0, 0.9, 0));
                        }
                }
                w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.4f);
            }
            default -> {
                for (LivingEntity t : near(p, 9)) { t.damage(8, p); push(t, p.getLocation(), 1.0, 0.8); }
                w.spawnParticle(Particle.EXPLOSION, p.getLocation(), 10, 4, 0.3, 4, 0);
                w.spawnParticle(Particle.BLOCK, p.getLocation(), 120, 4.5, 0.3, 4.5, 0.2, dirt);
                w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.3f);
            }
        }
    }
}

class MetalPower extends PowerModule {
    MetalPower(Enderforge pl) { super(pl, "metal-power", "Metal Power", 13, "Armor Shield", "Spikes", "Iron Slam"); }

    @Override
    void cast(Player p, int ph) {
        World w = p.getWorld();
        switch (ph) {
            case 1 -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                w.spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 50, 0.6, 0.9, 0.6, 0.1);
                w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
            }
            case 2 -> {
                for (LivingEntity t : near(p, 4)) { t.damage(5, p); push(t, p.getLocation(), 0.5, 0.2); }
                w.spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 80, 2, 0.8, 2, 0.3);
                w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f);
            }
            default -> {
                for (LivingEntity t : near(p, 6)) { t.damage(7, p); push(t, p.getLocation(), 1.2, 0.5); }
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                w.spawnParticle(Particle.EXPLOSION, p.getLocation(), 6, 2.5, 0.3, 2.5, 0);
                w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
            }
        }
    }
}

class FireWaterPower extends PowerModule {
    FireWaterPower(Enderforge pl) { super(pl, "fire-water-power", "Fire & Water Power", 13, "Flame Burst", "Water Wave", "Steam Blast"); }

    @Override
    void cast(Player p, int ph) {
        World w = p.getWorld();
        switch (ph) {
            case 1 -> {
                Vector dir = p.getLocation().getDirection();
                for (LivingEntity t : near(p, 6)) {
                    Vector to = t.getLocation().toVector().subtract(p.getLocation().toVector());
                    if (to.lengthSquared() > 0.0001 && to.normalize().dot(dir) > 0.5) {
                        t.damage(3, p);
                        t.setFireTicks(100);
                    }
                }
                for (double d = 1; d < 6; d += 0.5)
                    w.spawnParticle(Particle.FLAME, p.getEyeLocation().add(dir.clone().multiply(d)), 4, 0.3, 0.3, 0.3, 0.02);
                w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }
            case 2 -> {
                p.setFireTicks(0);
                for (LivingEntity t : near(p, 7)) { t.setFireTicks(0); push(t, p.getLocation(), 1.4, 0.3); }
                w.spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 200, 3.5, 0.8, 3.5, 0.2);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.5f, 0.8f);
            }
            default -> {
                for (LivingEntity t : near(p, 6)) {
                    t.damage(6, p);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                }
                w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 150, 3, 1, 3, 0.1);
                w.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.6f);
            }
        }
    }
}

/** Elemental Power Switch: choose your element, then /power (or right-click the wand) to cast. */
class ElementalSwitch extends EfModule {
    static final Map<String, String> ELEMENTS = new LinkedHashMap<>();
    static {
        ELEMENTS.put("grass", "grass-power");
        ELEMENTS.put("earthquake", "earthquake-power");
        ELEMENTS.put("metal", "metal-power");
        ELEMENTS.put("firewater", "fire-water-power");
    }
    private final NamespacedKey wandKey;

    ElementalSwitch(Enderforge pl) {
        super(pl, "elemental-switch", 10);
        wandKey = new NamespacedKey(pl, "power_wand");
    }

    @Override public List<String> commands() { return List.of("power"); }

    @Override
    public List<String> tab(String[] a) {
        if (a.length == 1) return List.of("use", "element", "wand", "info");
        if (a.length == 2 && a[0].equalsIgnoreCase("element")) return new ArrayList<>(ELEMENTS.keySet());
        return List.of();
    }

    @Override
    public boolean onCommand(Player p, String[] a) {
        String sub = a.length == 0 ? "use" : a[0].toLowerCase();
        switch (sub) {
            case "use" -> useActive(p);
            case "info" -> msg(p, "Element: " + plugin.data().element(p.getUniqueId())
                    + "  (grass, earthquake, metal, firewater)  Each use advances Phase 1 > 2 > 3.");
            case "wand" -> {
                ItemStack wand = new ItemStack(Material.BLAZE_ROD);
                ItemMeta meta = wand.getItemMeta();
                meta.displayName(Component.text("Enderforge Power Wand", NamedTextColor.LIGHT_PURPLE));
                meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
                wand.setItemMeta(meta);
                give(p, wand);
                msg(p, "Right-click with the wand to cast your power.");
            }
            case "element" -> {
                String e = a.length > 1 ? a[1].toLowerCase() : "";
                if (!ELEMENTS.containsKey(e)) { msg(p, "Choose: " + String.join(", ", ELEMENTS.keySet()), NamedTextColor.RED); return true; }
                if (!ready(p)) return true;
                plugin.data().setElement(p.getUniqueId(), e);
                msg(p, "Element switched to " + e + ".", NamedTextColor.GOLD);
            }
            default -> msg(p, "Usage: /power <use|element <name>|wand|info>");
        }
        return true;
    }

    void useActive(Player p) {
        String key = plugin.data().element(p.getUniqueId());
        if (plugin.module(ELEMENTS.getOrDefault(key, "grass-power")) instanceof PowerModule pm) pm.use(p);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        if (!it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        useActive(e.getPlayer());
    }
}
