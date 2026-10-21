package dev.enderforge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class AutoMiner extends EfModule {
    private static final Set<Material> STONES = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE,
            Material.COBBLED_DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF,
            Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE);

    AutoMiner(Enderforge pl) { super(pl, "auto-miner", 10); }
    @Override public List<String> commands() { return List.of("automine"); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("_PICKAXE")) { msg(p, "Hold a pickaxe in your main hand."); return true; }
        if (!ready(p)) return true;
        int r = cfgInt("radius", 2), n = 0;
        Block c = p.getLocation().getBlock();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            Block b = c.getRelative(x, y, z);
            Material m = b.getType();
            if (STONES.contains(m) || m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) {
                b.breakNaturally(tool);
                n++;
            }
        }
        msg(p, "Auto Miner broke " + n + " blocks.");
        return true;
    }
}

class AutoFarmer extends EfModule {
    AutoFarmer(Enderforge pl) { super(pl, "auto-farmer", 13); }
    @Override public List<String> commands() { return List.of("autofarm"); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        if (!ready(p)) return true;
        int r = cfgInt("radius", 5), n = 0;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Block c = p.getLocation().getBlock();
        for (int x = -r; x <= r; x++) for (int y = -1; y <= 1; y++) for (int z = -r; z <= r; z++) {
            Block b = c.getRelative(x, y, z);
            if (!(Tag.CROPS.isTagged(b.getType()) || b.getType() == Material.NETHER_WART)) continue;
            if (b.getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge()) {
                Location l = b.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack d : b.getDrops(tool)) b.getWorld().dropItemNaturally(l, d);
                age.setAge(0);
                b.setBlockData(age);
                n++;
            }
        }
        msg(p, "Auto Farmer harvested and replanted " + n + " crops.");
        return true;
    }
}

class AutoLumberjack extends EfModule {
    private final Set<UUID> active = new HashSet<>();
    AutoLumberjack(Enderforge pl) { super(pl, "auto-lumberjack", 13); }
    @Override public List<String> commands() { return List.of("lumberjack"); }
    @Override protected void onDisable() { active.clear(); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        if (!active.remove(p.getUniqueId())) { active.add(p.getUniqueId()); msg(p, "Auto Lumberjack ON. Break a log with an axe."); }
        else msg(p, "Auto Lumberjack OFF.");
        return true;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block start = e.getBlock();
        if (!active.contains(p.getUniqueId()) || !Tag.LOGS.isTagged(start.getType())) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("_AXE")) return;
        if (!ready(p)) return;
        int max = cfgInt("max-logs", 150);
        Set<Block> seen = new HashSet<>();
        Deque<Block> q = new ArrayDeque<>();
        seen.add(start); q.add(start);
        while (!q.isEmpty() && seen.size() < max) {
            Block b = q.poll();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block nb = b.getRelative(x, y, z);
                if (nb.getType() == start.getType() && seen.add(nb)) q.add(nb);
            }
        }
        for (Block b : seen) if (!b.equals(start)) b.breakNaturally(tool);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }
}

class AutoFisher extends EfModule {
    private final Set<UUID> active = new HashSet<>();
    AutoFisher(Enderforge pl) { super(pl, "auto-fisher", 10); }
    @Override public List<String> commands() { return List.of("autofish"); }

    @Override
    protected void onEnable() {
        every(Math.max(1, cooldownSeconds()) * 20L, () -> {
            for (UUID id : new ArrayList<>(active)) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p == null) { active.remove(id); continue; }
                boolean rod = p.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD
                        || p.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD;
                if (!rod) continue;
                int r = ThreadLocalRandom.current().nextInt(100);
                Material loot = r < 55 ? Material.COD : r < 80 ? Material.SALMON : r < 92 ? Material.PUFFERFISH
                        : r < 97 ? Material.TROPICAL_FISH : r < 98 ? Material.NAME_TAG : r < 99 ? Material.SADDLE : Material.NAUTILUS_SHELL;
                give(p, new ItemStack(loot));
                p.giveExp(2);
            }
        });
    }

    @Override protected void onDisable() { active.clear(); }

    @Override
    public boolean onCommand(Player p, String[] a) {
        if (!active.remove(p.getUniqueId())) { active.add(p.getUniqueId()); msg(p, "Auto Fisher ON. Hold a fishing rod."); }
        else msg(p, "Auto Fisher OFF.");
        return true;
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }
}
