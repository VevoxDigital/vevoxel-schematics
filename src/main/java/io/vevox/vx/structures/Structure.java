package io.vevox.vx.structures;

import com.google.common.base.*;
import net.minecraft.server.v1_10_R1.*;
import net.minecraft.server.v1_10_R1.Block;
import org.apache.commons.lang3.Validate;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.craftbukkit.v1_10_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_10_R1.block.CraftBlockState;
import org.bukkit.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;
import java.util.Optional;

/**
 * A "Structure" is any collection of blocks created by the <code>Structure Block</code> or by this plug-in and contains
 * a three-dimensional matrix of blocks than can be saved and loaded as needed.
 *
 * @author Matthew Struble
 * @since 0.1.0
 */
@SuppressWarnings("unused WeakerAccess")
public class Structure implements Cloneable {

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

    Map<IBlockState, Object> stateValueMap(Collection<IBlockState<?>> states) {
      Map<IBlockState, Object> map = new HashMap<>();
      states.stream().filter(s -> properties.containsKey(s.a()))
          .forEach(s -> {
            @SuppressWarnings("Guava")
            com.google.common.base.Optional<?> valOpt = s.b(properties.get(s.a()));
            if (valOpt.isPresent()) map.put(s, valOpt.get());
          });
      return map;
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

    Vector pos;
    int state;

    StructureBlockItem(NBTTagCompound block) {
      state = block.getInt("state");

      NBTTagList pos = block.getList("pos", 4);
      this.pos = new Vector(pos.c(0), pos.c(1), pos.c(2));
    }

    StructureBlockItem(Vector pos, int state) {
      this.pos = pos;
      this.state = state;
    }



    NBTTagCompound toTag() {
      NBTTagCompound compound = new NBTTagCompound();
      compound.setInt("state", state);

      NBTTagList pos = new NBTTagList();
      pos.add(new NBTTagInt(this.pos.getBlockX()));
      pos.add(new NBTTagInt(this.pos.getBlockY()));
      pos.add(new NBTTagInt(this.pos.getBlockZ()));
      compound.set("pos", pos);
      return compound;
    }

    public boolean isAt(Vector vector) {
      return vector.equals(pos);
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
   * @throws IOException              General I/O read errors.
   * @throws IllegalArgumentException If the input stream is null.
   * @see Structure#Structure(File)
   * @see Structure#Structure(String, int, Location, Vector)
   * @since 0.1.0
   */
  public Structure(InputStream input) throws IOException, IllegalArgumentException {
    Validate.notNull(input);
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
            blocks.add(new StructureBlockItem(new Vector(x, y, z), getStateIndex(new StructurePaletteItem(data))));
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

  private Optional<StructurePaletteItem> getPaletteAt(Vector vector) {
    Optional<StructureBlockItem> block = blocks.stream().filter(b -> b.isAt(vector)).findFirst();
    return block.isPresent() ? Optional.of(palette.get(block.get().state)) : Optional.empty();
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

  /**
   * Saves the structure to disk at the given file. The file should end with the <code>.nbt</code> extension,
   * but this will not be enforced.
   *
   * @param file The file to save to.
   *
   * @throws IOException              I/O write errors.
   * @throws FileNotFoundException    If the file does not exist.
   * @throws IllegalArgumentException if the file is null.
   */
  public void save(File file) throws IOException, IllegalArgumentException {
    Validate.notNull(file);
    save(new FileOutputStream(file));
  }

  /**
   * Saves the structure to the given output stream.
   * @param out The output stream to save to.
   * @throws IOException I/O write errors.
   * @throws IllegalArgumentException If the stream is null.
   */
  public void save(OutputStream out) throws IOException, IllegalArgumentException {
    NBTTagCompound compound = new NBTTagCompound();
    compound.setString("author", author);
    compound.setInt("version", version);

    NBTTagList blocks = new NBTTagList();
    this.blocks.stream().map(StructureBlockItem::toTag).forEach(blocks::add);
    compound.set("blocks", blocks);

    NBTTagList palette = new NBTTagList();
    this.palette.stream().map(StructurePaletteItem::toTag).forEach(palette::add);
    compound.set("palette", palette);

    NBTTagList entities = new NBTTagList();
    compound.set("entities", entities);

    NBTTagList size = new NBTTagList();
    size.add(new NBTTagInt(this.size.getBlockX()));
    size.add(new NBTTagInt(this.size.getBlockY()));
    size.add(new NBTTagInt(this.size.getBlockZ()));

    NBTCompressedStreamTools.a(compound, out);
  }

  // TODO Save to world.

  @Override
  public Structure clone() {
    try {
      return (Structure) super.clone();
    } catch (CloneNotSupportedException e) {
      return copy(author, version);
    }
  }

}
