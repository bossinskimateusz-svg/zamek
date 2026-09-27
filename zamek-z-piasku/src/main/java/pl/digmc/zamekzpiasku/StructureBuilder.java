package pl.digmc.zamekzpiasku;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Buduje (i pozniej usuwa) "Zamek z Piasku": kwadratowa budowla z blankami,
 * czterema wiezyczkami na rogach, oknami, wejsciem i swietlikiem w dachu
 * (otwarty kwadratowy otwor, przez ktory widac niebo - tak jak w oryginalnym
 * evencie).
 */
public class StructureBuilder {

    /** Prostopadloscienny obszar budowli, uzywany do sprawdzania czy zabojstwo liczy sie do bonusu. */
    public record Region(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
                return false;
            }
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            return x >= minX && x <= maxX + 1
                    && y >= minY && y <= maxY + 1
                    && z >= minZ && z <= maxZ + 1;
        }
    }

    /** Zapamietuje oryginalna zawartosc terenu, zeby dalo sie ja przywrocic po evencie. */
    public static final class Snapshot {
        private final World world;
        private final List<int[]> coords = new ArrayList<>();
        private final List<BlockData> originalData = new ArrayList<>();
        final Region region;

        private Snapshot(World world, Region region) {
            this.world = world;
            this.region = region;
        }

        private void remember(Block block) {
            coords.add(new int[]{block.getX(), block.getY(), block.getZ()});
            originalData.add(block.getBlockData().clone());
        }
    }

    public Snapshot build(Location center, ConfigurationSection structureCfg) {
        World world = center.getWorld();
        if (world == null) {
            throw new IllegalStateException("Swiat lokalizacji eventu jest null - sprawdz config.yml (location.world)");
        }

        int size = Math.max(7, structureCfg.getInt("size", 15));
        if (size % 2 == 0) {
            size += 1; // wymuszamy nieparzysty rozmiar, zeby srodek byl jednym blokiem
        }
        int wallHeight = Math.max(3, structureCfg.getInt("wall-height", 6));
        int towerHeight = Math.max(wallHeight + 2, structureCfg.getInt("tower-height", 9));
        int skylight = Math.min(size - 4, Math.max(3, structureCfg.getInt("skylight-size", 5)));

        Material wallMat = materialOr(structureCfg.getString("materials.wall"), Material.SANDSTONE);
        Material accentMat = materialOr(structureCfg.getString("materials.wall-accent"), Material.CHISELED_SANDSTONE);
        Material floorMat = materialOr(structureCfg.getString("materials.floor"), Material.ORANGE_TERRACOTTA);
        Material windowMat = materialOr(structureCfg.getString("materials.window"), Material.GLASS_PANE);
        Material towerMat = materialOr(structureCfg.getString("materials.tower"), Material.SANDSTONE);
        Material towerTopMat = materialOr(structureCfg.getString("materials.tower-top"), Material.SMOOTH_SANDSTONE_SLAB);

        int half = size / 2;
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();

        int minX = baseX - half;
        int maxX = baseX + half;
        int minZ = baseZ - half;
        int maxZ = baseZ + half;

        Region region = new Region(world, minX, maxX, baseY - 1, baseY + towerHeight, minZ, maxZ);
        Snapshot snapshot = new Snapshot(world, region);

        // Podloga wewnatrz zamku
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(snapshot, world, x, baseY - 1, z, floorMat);
            }
        }

        // Sciany (obwod kwadratu) - z oknami i wejsciem
        int doorHalfWidth = 1; // wejscie szerokosci 2 blokow
        for (int y = 0; y < wallHeight; y++) {
            for (int x = minX; x <= maxX; x++) {
                placeWallColumn(snapshot, world, x, baseY + y, minZ, y, wallHeight,
                        x - minX, wallMat, accentMat, windowMat, x == minX || x == maxX);
                placeWallColumn(snapshot, world, x, baseY + y, maxZ, y, wallHeight,
                        x - minX, wallMat, accentMat, windowMat, x == minX || x == maxX);
            }
            for (int z = minZ; z <= maxZ; z++) {
                if (z == minZ || z == maxZ) {
                    continue; // rogi juz ustawione powyzej
                }
                placeWallColumn(snapshot, world, minX, baseY + y, z, y, wallHeight,
                        z - minZ, wallMat, accentMat, windowMat, false);
                placeWallColumn(snapshot, world, maxX, baseY + y, z, y, wallHeight,
                        z - minZ, wallMat, accentMat, windowMat, false);
            }
        }

        // Dach z kwadratowym swietlikiem na srodku
        int roofY = baseY + wallHeight;
        int skylightHalf = skylight / 2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean inSkylight = Math.abs(x - baseX) <= skylightHalf && Math.abs(z - baseZ) <= skylightHalf;
                if (!inSkylight) {
                    setBlock(snapshot, world, x, roofY, z, wallMat);
                }
            }
        }

        // Wejscie - przebicie sciany od strony poludniowej (maxZ), na wysokosci 3 blokow
        for (int y = 0; y < 3; y++) {
            for (int x = baseX - doorHalfWidth; x <= baseX + doorHalfWidth; x++) {
                setBlock(snapshot, world, x, baseY + y, maxZ, Material.AIR);
            }
        }

        // Wiezyczki na 4 rogach
        buildTower(snapshot, world, minX, minZ, baseY, towerHeight, towerMat, towerTopMat);
        buildTower(snapshot, world, maxX, minZ, baseY, towerHeight, towerMat, towerTopMat);
        buildTower(snapshot, world, minX, maxZ, baseY, towerHeight, towerMat, towerTopMat);
        buildTower(snapshot, world, maxX, maxZ, baseY, towerHeight, towerMat, towerTopMat);

        return snapshot;
    }

    private void placeWallColumn(Snapshot snapshot, World world, int x, int y, int wallZ,
                                  int relativeY, int wallHeight, int wallPos,
                                  Material wallMat, Material accentMat, Material windowMat,
                                  boolean isCorner) {
        boolean isWindowRow = relativeY == wallHeight / 2;
        boolean isAccentRow = relativeY == 0 || relativeY == wallHeight - 1;

        Material toPlace;
        if (isCorner) {
            toPlace = accentMat;
        } else if (isWindowRow && (wallPos % 3 == 0)) {
            toPlace = windowMat;
        } else if (isAccentRow) {
            toPlace = accentMat;
        } else {
            toPlace = wallMat;
        }
        setBlock(snapshot, world, x, y, wallZ, toPlace);
    }

    private void buildTower(Snapshot snapshot, World world, int cornerX, int cornerZ, int baseY,
                             int towerHeight, Material towerMat, Material topMat) {
        // kazda wiezyczka to slup 3x3 (pusty w srodku) w rogu budowli
        for (int y = -1; y <= towerHeight; y++) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    boolean isPerimeter = ox == -1 || ox == 1 || oz == -1 || oz == 1;
                    if (!isPerimeter) {
                        continue; // srodek wiezy jest pusty w srodku (mozna wejsc)
                    }
                    Material mat = (y == towerHeight) ? topMat : towerMat;
                    setBlock(snapshot, world, cornerX + ox, baseY + y, cornerZ + oz, mat);
                }
            }
        }
        // proste blanki na szczycie wiezy (co drugi blok wyzszy o 1)
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                boolean isPerimeter = ox == -1 || ox == 1 || oz == -1 || oz == 1;
                if (isPerimeter && ((ox + oz) % 2 == 0)) {
                    setBlock(snapshot, world, cornerX + ox, baseY + towerHeight + 1, cornerZ + oz, towerMat);
                }
            }
        }
    }

    private void setBlock(Snapshot snapshot, World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        snapshot.remember(block);
        block.setType(material, false);
    }

    private Material materialOr(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Przywraca teren do stanu sprzed wybudowania zamku. */
    public void remove(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (int i = snapshot.coords.size() - 1; i >= 0; i--) {
            int[] c = snapshot.coords.get(i);
            BlockData data = snapshot.originalData.get(i);
            snapshot.world.getBlockAt(c[0], c[1], c[2]).setBlockData(data, false);
        }
    }
}
