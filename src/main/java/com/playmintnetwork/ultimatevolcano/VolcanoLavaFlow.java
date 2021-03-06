package com.playmintnetwork.ultimatevolcano;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.plugin.PluginManager;

import java.util.*;

public class VolcanoLavaFlow implements Listener {
    public Volcano volcano;
    private Random rand = new Random();
    public List<Chunk> lavaFlowChunks = new ArrayList<>();
    public List<VolcanoLavaCoolData> lavaCoolData = new ArrayList<>();
    public List<Block> lavaFlowingBlocks = new ArrayList<>();
    private int lavaFlowScheduleId = -1;
    private int lavaCoolScheduleId = -1;
    public VolcanoLavaFlowSettings settings = new VolcanoLavaFlowSettings();

    // core methods

    public VolcanoLavaFlow() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(this, MainPlugin.plugin);
    }

    public void registerEventHandler() {
        Bukkit.getServer().getPluginManager().registerEvents(this, MainPlugin.plugin);
    }

    // core scheduler methods


    public void registerTask() {
        if (lavaFlowScheduleId == -1) {
            lavaFlowScheduleId = MainPlugin.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(MainPlugin.plugin, () -> {
                flowLavaToVolcano();
                volcano.updateData();
            },0L,(long)settings.updateRate);
        }
        if (lavaCoolScheduleId == -1) {
            lavaCoolScheduleId = MainPlugin.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(MainPlugin.plugin, () -> {
                coolLavaOnVolcanoIteration();
                volcano.updateData();
            },0L,20L);
        }
    }

    public void unregisterTask() {
        if (lavaFlowScheduleId != -1) {
            MainPlugin.plugin.getServer().getScheduler().cancelTask(lavaFlowScheduleId);
            lavaFlowScheduleId = -1;
        }
        if (lavaCoolScheduleId != -1) {
            MainPlugin.plugin.getServer().getScheduler().cancelTask(lavaCoolScheduleId);
            lavaCoolScheduleId = -1;
        }
    }

    // explosion

    public boolean explodeLava(Block block) {
        int n = rand.nextInt(500) + 1;
        //Classic!
        if ((10 <= n) && (n < 20)) {
            makeExplosion(block, 2F, true, false);
            return true;
        } else if (n == 50) {
            makeExplosion(block, 2F, true, false);
            return true;
        } else if (n == 44) {
            makeExplosion(block, 6F, true, true);
            return true;
        } else if (n == 4) {
            makeExplosion(block, 4F, true, false);
            return true;
        } else {
            return true;
        }
    }

    public boolean lavaCollideWater(Block block) {
        int n = rand.nextInt(100) + 1;
        //Classic!
        if ((1 <= n) && (n < 20)) {
            makeExplosion(block, 1F, true, false);
            return true;
        } else if (n == 50) {
            makeExplosion(block, 2F, true, false);
            return true;
        } else if (n == 44) {
            makeExplosion(block, 6F, true, true);
            return true;
        } else if (n == 4) {
            makeExplosion(block, 4F, true, false);
            return true;
        } else {
            return true;
        }
    }

    // utility function

    public static void makeExplosion(Block block, float power, boolean setFire, boolean breakBlocks) {
        block.getLocation().getWorld().createExplosion(block.getLocation().getX(), block.getLocation().getY(), block.getLocation().getZ(), power, setFire, breakBlocks);
    }

    public static void blockSet(Block block, Material material) {
        if (material == null) return;
        block.setType(material);
    }

    //

    public static Material getBlockAfterBurned(Material material) {
        switch(material) {
            case SAND:
            case SANDSTONE:
                return Material.GLASS;
            case GRAVEL:
            case DIRT:
            case GRASS_BLOCK:
            case CLAY:
            case LEGACY_CONCRETE:
            default:
                return Material.STONE;
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {

        // new lava flow,
        /*
            If you set the count of the particle to 0, the "offset" should be treated as motion parameters for the particle.
            The particle system in Minecraft is sometimes a bit weird, but that's just how it works.

            (Edit: And yes, the "extra" argument will determine the speed of the particle)

            https://www.spigotmc.org/threads/spawn-a-campfire-particle.403246/
        */

        int tickFactor = 20 / settings.updateRate;

        Block block = event.getBlock();
        Block toBlock = event.getToBlock();

        // this is lava. flow it.
        if (lavaFlowingBlocks.contains(block)) {

            // force load chunk.
            if (!volcano.isChunkLoaded()) volcano.location.getChunk().load();

            if (!lavaFlowChunks.contains(toBlock.getChunk())) {
                lavaFlowChunks.add(toBlock.getLocation().getChunk());
                toBlock.getLocation().getChunk().setForceLoaded(true);
            }

            if (volcano.generator.throat && volcano.inCrater(block.getLocation())) {
                return;
            }

            // load toBlock chunk
            Chunk toBlockChunk = toBlock.getLocation().getChunk();
            if (!toBlockChunk.isLoaded()) {
                toBlockChunk.load();
            }

            Block nearByBlock = block;
            for (int a = 0; a < 6; a++) {
                switch (a) {
                    case 0:
                        nearByBlock = block.getRelative(BlockFace.SOUTH);
                        break;
                    case 1:
                        nearByBlock = block.getRelative(BlockFace.WEST);
                        break;
                    case 2:
                        nearByBlock = block.getRelative(BlockFace.NORTH);
                        break;
                    case 3:
                        nearByBlock = block.getRelative(BlockFace.EAST);
                        break;
                    case 4:
                        nearByBlock = block.getRelative(BlockFace.UP);
                        break;
                    case 5:
                        nearByBlock = block.getRelative(BlockFace.DOWN);
                        break;
                    default:
                        break;
                }

                if (nearByBlock.getType() == Material.AIR) {
                    continue;
                }

                if (Arrays.asList(VolcanoLavaFlowExplode.explode).contains(nearByBlock.getType())) {
                    if (volcano.affected(nearByBlock.getLocation())) {
                        if (Arrays.asList(VolcanoLavaFlowExplode.explode).contains(nearByBlock.getType())) {
                            blockSet(nearByBlock, Material.LAVA);
                            explodeLava(nearByBlock);
                            final Block theBlock = nearByBlock;
                            lavaCoolData.add(new VolcanoLavaCoolData(theBlock, volcano.getRandomBlock(),
                                    settings.flowed * tickFactor));
                            lavaFlowingBlocks.add(theBlock);
                        }
                    }
                } else if (Arrays.asList(VolcanoLavaFlowExplode.explodeAndRemove).contains(nearByBlock.getType())) {
                    lavaCollideWater(nearByBlock);
                    //final Block theBlock = nearByBlock;
                    //nearByBlock.setType(Material.AIR);
                }

                if (Arrays.asList(VolcanoLavaFlowExplode.blockToBurned).contains(nearByBlock.getType())) {
                    nearByBlock.setType(getBlockAfterBurned(nearByBlock.getType()));
                }
            }

            lavaCoolData.add(new VolcanoLavaCoolData(toBlock, volcano.getRandomBlock(), settings.flowed * tickFactor));
            lavaFlowingBlocks.add(toBlock);

        }
    }

    private long nextFlowTime = 0;

    public void flowLavaToVolcano() {
        if (volcano.enabled) {

            long timeNow = System.currentTimeMillis();
            if (System.currentTimeMillis() >= nextFlowTime) {
                int tickFactor = 20 / settings.updateRate;

                volcano.generateSmoke();
                Block whereToFlow = volcano.getRandomLavaFlowCraterBlock();
                whereToFlow.setType(Material.LAVA);
                lavaCoolData.add(new VolcanoLavaCoolData(whereToFlow, volcano.getRandomBlock(), settings.flowed * tickFactor));
                lavaFlowingBlocks.add(whereToFlow);
                nextFlowTime = timeNow + settings.delayFlowed * (1000 * settings.updateRate / 20);
            }
        }
    }

    public void coolLavaOnVolcanoIteration() {
        Iterator<VolcanoLavaCoolData> coolDataIterator = lavaCoolData.iterator();
        while (coolDataIterator.hasNext()) {
            VolcanoLavaCoolData coolData = coolDataIterator.next();
            coolData.tickPass();
            if (coolData.tickPassed()) {
                if (lavaFlowingBlocks.contains(coolData.block)) {
                    lavaFlowingBlocks.remove(coolData.block);
                }
                coolData.coolDown();
                coolDataIterator.remove();
            }
        }
    }
}

class VolcanoLavaFlowDefaultSettings {
    public static int flowed = 6;
    public static int delayFlowed = 3;
    public static int updateRate = 20;
}

class VolcanoLavaFlowSettings {
    public int flowed = VolcanoLavaFlowDefaultSettings.flowed;
    public int delayFlowed = VolcanoLavaFlowDefaultSettings.delayFlowed;
    public int updateRate = VolcanoLavaFlowDefaultSettings.updateRate;
}

class VolcanoLavaCoolData {
    public int ticks;
    public Block block;
    public Material material;

    VolcanoLavaCoolData(Block block, Material material, int ticks) {
        this.ticks = ticks;
        this.block = block;
        this.material = material;
    }

    public void tickPass() {
        if (this.tickPassed()) { this.coolDown(); }
        else { this.ticks--; }
    }

    public boolean tickPassed() {
        return this.ticks <= 0;
    }

    public void coolDown() {
        block.setType(material);
    }

    public void forceCoolDown() {
        this.ticks = 0;
        block.setType(material);
    }
}

interface VolcanoLavaFlowExplode {
    public static Material[] explode = { Material.BIRCH_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.JUNGLE_LOG, Material.OAK_LOG, Material.SPRUCE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_OAK_LOG,
            Material.STRIPPED_SPRUCE_LOG, Material.ACACIA_LEAVES, Material.BIRCH_LEAVES, Material.DARK_OAK_LEAVES, Material.JUNGLE_LEAVES, Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES, Material.GRASS, Material.TALL_GRASS, Material.SUNFLOWER, Material.ALLIUM, Material.POTTED_ALLIUM, Material.AZURE_BLUET,
            Material.POTTED_AZURE_BLUET, Material.SPRUCE_SAPLING, Material.ACACIA_SAPLING, Material.BIRCH_SAPLING, Material.DARK_OAK_SAPLING,
            Material.JUNGLE_SAPLING, Material.OAK_SAPLING, Material.POTTED_ACACIA_SAPLING, Material.POTTED_BIRCH_SAPLING, Material.POTTED_DARK_OAK_SAPLING,
            Material.POTTED_JUNGLE_SAPLING, Material.POTTED_OAK_SAPLING, Material.POTTED_SPRUCE_SAPLING, Material.POTTED_BLUE_ORCHID, Material.BLUE_ORCHID,
            Material.POTTED_BROWN_MUSHROOM, Material.BROWN_MUSHROOM, Material.BROWN_MUSHROOM_BLOCK, Material.POTTED_CACTUS, Material.CACTUS,
            Material.POTTED_DANDELION, Material.DANDELION, Material.POTTED_DEAD_BUSH, Material.DEAD_BUSH, Material.GLASS, Material.LEVER,
            Material.POTTED_FERN, Material.FERN, Material.LARGE_FERN, Material.POTTED_ORANGE_TULIP, Material.ORANGE_TULIP, Material.POTTED_OXEYE_DAISY,
            Material.OXEYE_DAISY, Material.POTTED_PINK_TULIP, Material.PINK_TULIP, Material.POTTED_POPPY, Material.POPPY, Material.POTTED_RED_MUSHROOM,
            Material.RED_MUSHROOM, Material.RED_MUSHROOM_BLOCK, Material.POTTED_RED_TULIP, Material.RED_TULIP, Material.POTTED_WHITE_TULIP,
            Material.WHITE_TULIP, Material.SPRUCE_SIGN, Material.ACACIA_SIGN, Material.SPRUCE_WALL_SIGN, Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
            Material.ACACIA_WALL_SIGN, Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN, Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN, Material.OAK_SIGN,
            Material.OAK_WALL_SIGN, Material.ACACIA_WOOD, Material.BIRCH_WOOD, Material.DARK_OAK_WOOD, Material.JUNGLE_WOOD, Material.OAK_WOOD,
            Material.SPRUCE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.DARK_OAK_DOOR, Material.ACACIA_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.OAK_DOOR, Material.ACACIA_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS, Material.DARK_OAK_STAIRS, Material.JUNGLE_STAIRS,
            Material.OAK_STAIRS, Material.ACACIA_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE };
    public static Material[] explodeAndRemove = { Material.WATER, Material.LEGACY_STATIONARY_WATER, Material.SNOW, Material.SNOW_BLOCK, Material.KELP, Material.KELP_PLANT };
    public static Material[] lavaCooled = { Material.COBBLESTONE, Material.STONE, Material.OBSIDIAN };
    public static Material[] blockToBurned = { Material.GRASS, Material.GRASS_BLOCK, Material.GRAVEL, Material.DIRT, Material.CLAY, Material.LEGACY_CONCRETE, Material.SAND };
}

