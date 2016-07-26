package io.vevox.vx.structures;

import net.minecraft.server.v1_10_R1.*;
import org.apache.commons.lang3.Validate;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_10_R1.CraftWorld;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A "Structure" is any collection of blocks created by the <code>Structure Block</code> or by this plug-in and contains
 * a three-dimensional matrix of blocks than can be saved and loaded as needed.
 * @author Matthew Struble
 * @since 0.1.0
 */
@SuppressWarnings("unused WeakerAccess")
public class Structure {

  /**
   * Mirroring of the loading structure.
   */
  public enum Mirror {
    NONE, LEFT_RIGHT, FRONT_BACK
  }

  /**
   * Rotation of the loading structure.
   */
  public enum Rotation {
    R_0, R_90, R_180, R_270
  }

  private static class StructurePaletteItem {

    Map<String, String> properties;
    String name;

    StructurePaletteItem(NBTTagCompound palette) {
      name = palette.getString("name");
      this.properties = new HashMap<>();
      palette.getCompound("properties").c().forEach(k -> this.properties.put(k, palette.getString(k)));
      updateProperties();
    }

    @SuppressWarnings("unchecked")
    StructurePaletteItem(IBlockData data) {
      name = data.getBlock().getName();
      properties = new HashMap<>();

      Collection<IBlockState> states = new HashSet<>();
      states.addAll(data.r());
      states.forEach(s -> properties.put(s.a(), s.a(data.get(s))));
    }

    private void updateProperties() {
      if (properties.containsKey("north")) properties.put("north", "false");
      if (properties.containsKey("south")) properties.put("south", "false");
      if (properties.containsKey("east")) properties.put("east", "false");
      if (properties.containsKey("west")) properties.put("west", "false");
    }

    NBTTagCompound toTag() {
      NBTTagCompound compound = new NBTTagCompound();
      compound.setString("name", name);

      NBTTagCompound properties = new NBTTagCompound();
      this.properties.forEach(properties::setString);
      compound.set("properties", properties);

      return compound;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof StructurePaletteItem)) return false;
      StructurePaletteItem other = (StructurePaletteItem) o;
      return other.name.equals(name) && !other.properties.equals(properties);
    }

  }

  private static class StructureBlockItem {

    BlockPosition pos;
    int state;

    StructureBlockItem(NBTTagCompound block) {
      state = block.getInt("state");

      int[] pos = block.getIntArray("pos");
      this.pos = new BlockPosition(pos[0], pos[1], pos[2]);
    }

    StructureBlockItem(BlockPosition pos, int state) {
      this.pos = pos;
      this.state = state;
    }

    NBTTagCompound toTag() {
      NBTTagCompound compound = new NBTTagCompound();
      compound.setInt("state", state);
      compound.setIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getY()});
      return compound;
    }

    boolean isAt(BlockPosition pos) {
      return this.pos.equals(pos);
    }

  }

  public final String author;
  public final int version;

  public final Vector size;

  private final List<StructureBlockItem> blocks;
  private final List<StructurePaletteItem> palette;
  // TODO Entities?

  /**
   * Loads a structure from the given file, ignoring size/complexity restrictions of the vanilla block.
   *
   * @param file The file to load from.
   *
   * @throws java.io.FileNotFoundException If the file could not be found.
   * @throws IOException                   General I/O read errors.
   * @see Structure#Structure(InputStream)
   * @see Structure#Structure(String, int, Location, Vector)
   * @since 0.1.0
   */
  public Structure(File file) throws IOException {
    this(new FileInputStream(file));
  }

  /**
   * Loaded a structure from the given input stream, ignoring size/complexity restrictions of the vanilla block.
   *
   * @param input The input stream to load from.
   *
   * @throws IOException General I/O read errors.
   * @see Structure#Structure(File)
   * @see Structure#Structure(String, int, Location, Vector)
   * @since 0.1.0
   */
  public Structure(InputStream input) throws IOException {
    NBTTagCompound compound = NBTCompressedStreamTools.a(input);

    author = compound.getString("author");
    version = compound.getInt("version");

    NBTTagList size = compound.getList("size", 4);
    this.size = new Vector(size.c(0), size.c(1), size.c(2));

    blocks = new ArrayList<>();
    palette = new ArrayList<>();

    NBTTagList palette = compound.getList("palette", 10);
    for (int i = 0; i < palette.size(); i++)
      this.palette.add(new StructurePaletteItem(palette.get(i)));

    NBTTagList blocks = compound.getList("blocks", 10);
    for (int i = 0; i < blocks.size(); i++)
      this.blocks.add(new StructureBlockItem(blocks.get(i)));
  }

  /**
   * Creates a new Structure with the given <code>author</code> and <code>version</code> from the blocks
   * at <code>corner</code> up to <code>size</code>. Any <code>minecraft:structure_void</code> or
   * <code>minecraft:structure_block</code> blocks caught inside the region will not be saved, as per the functionality
   * of the vanilla block.
   * <p>
   * <b>Note:</b> Unlike vanilla structure blocks, this method does not have a size or complexity limit. Be careful when
   * saving large and/or complicated (many different blocks) structures, as this can create extremely large files.
   *
   * @param author  The author of the new structure.
   * @param version The version of the new structure.
   * @param corner  The starting corner location.
   * @param size    The size to scan out to. Must be all positive non-zeros.
   *
   * @throws IllegalArgumentException If the author, corner, or size is null, the version is zero or less, or the
   *                                  size vector is not all positive.
   * @see Structure#Structure(File)
   * @see Structure#Structure(InputStream)
   * @since 0.1.0
   */
  public Structure(String author, int version, Location corner, Vector size) throws IllegalArgumentException {
    Validate.notNull(author);
    Validate.notNull(corner);
    Validate.notNull(size);
    Validate.isTrue(version > 0);
    Validate.isTrue(size.getBlockX() > 0 && size.getBlockY() > 0 && size.getBlockZ() > 0);
    this.author = author;
    this.version = version;
    this.size = size;

    blocks = new ArrayList<>();
    palette = new ArrayList<>();

    WorldServer world = ((CraftWorld) corner.getWorld()).getHandle();
    for (int x = corner.getBlockX(); x < size.getBlockX(); x++)
      for (int y = corner.getBlockY(); y < size.getBlockY(); y++)
        for (int z = corner.getBlockZ(); z < size.getBlockZ(); z++) {
          IBlockData data = world.c(new BlockPosition(x, y, z));
          if (!data.getBlock().getName().equals("minecraft:structure_void")
              && !data.getBlock().getName().equals("minecraft:structure_block"))
            blocks.add(new StructureBlockItem(new BlockPosition(x, y, z), getStateIndex(new StructurePaletteItem(data))));
        }
  }

  private Structure(String author, int version, Vector size, List<StructureBlockItem> blocks, List<StructurePaletteItem> palette) {
    this.author = author;
    this.version = version;
    this.blocks = blocks;
    this.palette = palette;
    this.size = size;
  }

  private int getStateIndex(StructurePaletteItem paletteItem) {
    int index = palette.indexOf(paletteItem);
    if (index < 0) palette.add(paletteItem);
    return index < 0 ? palette.size() - 1 : index;
  }

  /**
   * Copies this structure to a new structure with the given author, incrementing the version by one.
   *
   * @param author The new author.
   *
   * @return The newly copied structure.
   * @throws IllegalArgumentException If the given author is null.
   * @since 0.1.0
   */
  public Structure copy(String author) throws IllegalArgumentException {
    return copy(author, 0);
  }

  /**
   * Copies this structure to a new structure with the given author and version number. The same version number
   * as this structure <i>may</i> be used.
   *
   * @param author  The new author.
   * @param version The new version.
   *
   * @return The newly copied structure.
   * @throws IllegalArgumentException If the given author is null.
   * @since 0.1.0
   */
  public Structure copy(String author, int version) throws IllegalArgumentException {
    Validate.notNull(author);
    return new Structure(author, version > 0 ? version : this.version + 1, size, blocks, palette);
  }

  public void save(File file) throws IOException {

  }

  public void save(Location location, Mirror mirror, Rotation rotation) throws IllegalArgumentException {

  }

}
