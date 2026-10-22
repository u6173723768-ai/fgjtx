package com.mediaclient.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Sponge Schematic v2/v3 palette reader; air entries are skipped to avoid destructive clearing. */
public final class SchematicLoader {
    private SchematicLoader() {}

    public static SchematicData load(Path path) throws IOException {
        if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("Schematic file is too large (maximum compressed size is 32 MiB).");
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024));
        } catch (RuntimeException exception) {
            throw new IOException("Schematic NBT is invalid or exceeds the 64 MiB safety limit.", exception);
        }
        CompoundTag blocks = root.getCompoundOrEmpty("Blocks");
        if (blocks.isEmpty()) blocks = root;
        int width = readDimension(root, blocks, "Width");
        int height = readDimension(root, blocks, "Height");
        int length = readDimension(root, blocks, "Length");
        long volume = (long) width * height * length;
        if (width < 1 || height < 1 || length < 1 || volume > 2_000_000L)
            throw new IOException("Invalid or oversized schematic dimensions (max 2,000,000 blocks).");

        CompoundTag palette = blocks.getCompoundOrEmpty("Palette");
        if (palette.isEmpty()) palette = root.getCompoundOrEmpty("Palette");
        byte[] data = blocks.getByteArray("Data").orElseGet(() -> blocks.getByteArray("BlockData").orElse(new byte[0]));
        if (data.length == 0) data = root.getByteArray("BlockData").orElse(new byte[0]);
        if (palette.isEmpty() || data.length == 0) throw new IOException("This schematic has no Sponge Palette/BlockData; unsupported format.");

        Map<Integer, BlockState> states = new HashMap<>();
        for (String paletteEntry : palette.keySet()) {
            int paletteIndex = palette.getInt(paletteEntry).orElse(-1);
            if (paletteIndex >= 0) states.put(paletteIndex, parseState(paletteEntry));
        }
        List<SchematicData.Placement> placements = new ArrayList<>((int) Math.min(volume, 250_000));
        int offset = 0;
        for (int index = 0; index < volume; index++) {
            VarInt parsed = readVarInt(data, offset);
            if (parsed.bytesUsed == 0) throw new IOException("Schematic block data ended at block " + index + " of " + volume + '.');
            offset += parsed.bytesUsed;
            BlockState state = states.getOrDefault(parsed.value, Blocks.AIR.defaultBlockState());
            if (state.isAir()) continue;
            int x = index % width, z = (index / width) % length, y = index / (width * length);
            placements.add(new SchematicData.Placement(new BlockPos(x, y, z), state));
        }
        placements.sort(Comparator.comparingInt((SchematicData.Placement p) -> p.relativePos().getY())
                .thenComparingInt(p -> p.relativePos().getZ()).thenComparingInt(p -> p.relativePos().getX()));
        if (placements.isEmpty()) throw new IOException("Schematic contains no non-air blocks.");
        return new SchematicData(width, height, length, List.copyOf(placements));
    }

    private static int readDimension(CompoundTag root, CompoundTag blocks, String key) throws IOException {
        Optional<Short> shortValue = blocks.getShort(key);
        if (shortValue.isPresent()) return Short.toUnsignedInt(shortValue.get());
        Optional<Integer> intValue = blocks.getInt(key);
        if (intValue.isPresent()) return intValue.get();
        shortValue = root.getShort(key);
        if (shortValue.isPresent()) return Short.toUnsignedInt(shortValue.get());
        intValue = root.getInt(key);
        if (intValue.isPresent()) return intValue.get();
        throw new IOException("Schematic is missing dimension " + key + '.');
    }

    private static BlockState parseState(String text) {
        String id = text, properties = null;
        int open = text.indexOf('[');
        if (open >= 0 && text.endsWith("]")) { id = text.substring(0, open); properties = text.substring(open + 1, text.length() - 1); }
        Identifier location = Identifier.tryParse(id);
        if (location == null) return Blocks.AIR.defaultBlockState();
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(Blocks.AIR);
        BlockState state = block.defaultBlockState();
        if (properties == null || properties.isBlank()) return state;
        for (String pair : properties.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String name = pair.substring(0, eq).trim(), value = pair.substring(eq + 1).trim();
            Property<?> property = state.getProperties().stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
            if (property != null) state = applyProperty(state, property, value);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String text) {
        Optional value = property.getValue(text);
        return value.isPresent() ? state.setValue(property, (Comparable) value.get()) : state;
    }

    private static VarInt readVarInt(byte[] data, int offset) {
        int value = 0, position = 0;
        for (int i = 0; i < 5 && offset + i < data.length; i++) {
            int current = data[offset + i] & 0xFF;
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) return new VarInt(value, i + 1);
            position += 7;
        }
        return new VarInt(0, 0);
    }
    private record VarInt(int value, int bytesUsed) { }
}
