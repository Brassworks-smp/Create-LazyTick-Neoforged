package net.pinkcats.createlazytick.helper.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.pinkcats.createlazytick.CreateLazyTick;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.manager.ForcedActiveManager;
import net.pinkcats.createlazytick.manager.LazyTickStatCache;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandHelper {
    private static final int PAGE_SIZE = 15;

    public static class SortCriterion {
        public final LazyTickSortMode mode;
        public final boolean isReverse;

        public SortCriterion(LazyTickSortMode mode, boolean isReverse) {
            this.mode = mode;
            this.isReverse = isReverse;
        }
    }

    public static int executeList(CommandContext<CommandSourceStack> context, int page, List<SortCriterion> criteria,
                                  boolean globalReverse, Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter,
                                  String rawSortStr, String rawFilterStr) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        Map<BlockPos, LazyTickStatCache> forcedMachines = ForcedActiveManager.getForcedMachines(level);
        if (forcedMachines.isEmpty()) {
            source.sendSystemMessage(Component.translatable("createlazytick.message.no_non_default_machines").withStyle(ChatFormatting.GREEN));
            return 0;
        }

        CommandHelper.SortContext sortContext = CommandHelper.createSortContext(source, forcedMachines.keySet());

        List<Map.Entry<BlockPos, LazyTickStatCache>> rawDataSnapshot = new ArrayList<>(forcedMachines.entrySet());

        List<Map.Entry<BlockPos, LazyTickStatCache>> filteredEntries = new ArrayList<>();
        for (Map.Entry<BlockPos, LazyTickStatCache> entry : rawDataSnapshot) {
            if (filter.test(entry)) {
                filteredEntries.add(entry);
            }
        }

        if (filteredEntries.isEmpty()) {
            source.sendFailure(Component.translatable("createlazytick.message.no_filtered_records"));
            return 0;
        }

        try {
            Comparator<Map.Entry<BlockPos, LazyTickStatCache>> finalComparator = null;
            for (SortCriterion criterion : criteria) {

                boolean effectiveReverse = (criterion.isReverse != globalReverse);

                Comparator<Map.Entry<BlockPos, LazyTickStatCache>> modeComparator =
                        criterion.mode.getThreadSafeComparator(sortContext.getLoadedPositions(), sortContext.getPlayerPos(), effectiveReverse);

                if (finalComparator == null) {
                    finalComparator = modeComparator;
                } else {
                    finalComparator = finalComparator.thenComparing(modeComparator); 
                }
            }
            if (finalComparator != null) {
                filteredEntries.sort(finalComparator);
            }
        } catch (Exception e1) {
            source.sendFailure(Component.translatable("createlazytick.error.sort_internal_error"));
            mes.error("List sort error: " + e1.getMessage());

            try {
                filteredEntries.sort(LazyTickSortMode.DEFAULT.getThreadSafeComparator(sortContext.getLoadedPositions(),
                        sortContext.getPlayerPos(), false));
            } catch (Exception e2) {
                RestoreFailed(e2);
            }
        }

        CommandHelper.renderAllAndCleanData(source, level, filteredEntries, page, rawSortStr, globalReverse, rawFilterStr);

        return 1;
    }

    public static List<CommandHelper.SortCriterion> parseSortString(String input) throws CommandSyntaxException {
        if (input == null || input.isBlank()) {
            return Collections.singletonList(new CommandHelper.SortCriterion(LazyTickSortMode.DEFAULT, false));
        }

        String trimmed = FilterParser.stripBracesAndQuotes(input);

        String[] parts = trimmed.split("[,\\s]+");
        List<CommandHelper.SortCriterion> list = new ArrayList<>();

        for (String part : parts) {
            String cleanPart = part.trim();
            if (cleanPart.isEmpty()) continue;

            boolean isReverse = false;

            if (cleanPart.startsWith("!")) {
                isReverse = true;
                cleanPart = cleanPart.substring(1).trim();
            }

            LazyTickSortMode mode = LazyTickSortMode.byName(cleanPart);

            if (mode == null) {
                String available = "default, time, name, player, method, value, nearest, loaded";

                throw new SimpleCommandExceptionType(
                        Component.translatable(
                                "createlazytick.error.unknown_sort_mode",
                                mes.CharM(cleanPart).withStyle(ChatFormatting.UNDERLINE),
                                available
                        )
                ).create();
            }

            list.add(new CommandHelper.SortCriterion(mode, isReverse));
        }

        if (list.isEmpty()) {
            list.add(new CommandHelper.SortCriterion(LazyTickSortMode.DEFAULT, false));
        }

        return list;
    }

    public static int executeReset(CommandContext<CommandSourceStack> context, Component description, Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        Map<BlockPos, LazyTickStatCache> forcedMachines = ForcedActiveManager.getForcedMachines(level);
        if (forcedMachines.isEmpty()) {
            source.sendFailure(Component.translatable("createlazytick.reset.no_records"));
            return 0;
        }

        CommandHelper.SortContext sortContext = CommandHelper.createSortContext(source, forcedMachines.keySet());

        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, LazyTickStatCache> entry : forcedMachines.entrySet()) {

            if (filter.test(entry)) {

                if (sortContext.getLoadedPositions().contains(entry.getKey())) {
                    candidates.add(entry.getKey());
                }
            }
        }

        int count = ForcedActiveManager.executeBatchReset(level, candidates);

        if (count > 0) {
            source.sendSuccess(() -> Component.translatable(
                            "createlazytick.reset.success_loaded_only",
                            count,
                            description
                    ).append("\n")
                    .append(Component.translatable("createlazytick.reset.note_unloaded_unchanged")
                            .withStyle(ChatFormatting.GRAY)), true);
        } else {
            source.sendFailure(Component.translatable(
                    "createlazytick.reset.not_found_loaded_only",
                    description
            ));
        }

        return 1;
    }

    public static int executeDump(CommandContext<CommandSourceStack> context, List<SortCriterion> criteria,
                                  boolean globalReverse, Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter,
                                  String rawSortStr, String rawFilterStr) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        Map<BlockPos, LazyTickStatCache> forcedMachines = ForcedActiveManager.getForcedMachines(level);
        if (forcedMachines.isEmpty()) {
            source.sendFailure(Component.translatable("createlazytick.export.no_data"));
            return 0;
        }

        CommandHelper.SortContext sortContext = CommandHelper.createSortContext(source, forcedMachines.keySet());

        List<Map.Entry<BlockPos, LazyTickStatCache>> rawDataSnapshot = new ArrayList<>(forcedMachines.entrySet());

        Path serverRoot = source.getServer().getServerDirectory();
        Path dumpDir = serverRoot.resolve("dumps").resolve("createlazytick");

        List<Map.Entry<BlockPos, LazyTickStatCache>> resultList = new ArrayList<>();
        for (Map.Entry<BlockPos, LazyTickStatCache> entry : rawDataSnapshot) {
            if (filter.test(entry)) {
                resultList.add(entry);
            }
        }

        if (resultList.isEmpty()) {
            source.sendFailure(
                    Component.translatable("createlazytick.export.no_records_cancelled")
            );
            return 0;
        }

        try {
            Comparator<Map.Entry<BlockPos, LazyTickStatCache>> finalComparator = null;
            for (SortCriterion criterion : criteria) {

                boolean effectiveReverse = (criterion.isReverse != globalReverse);

                Comparator<Map.Entry<BlockPos, LazyTickStatCache>> modeComparator =
                        criterion.mode.getThreadSafeComparator(sortContext.getLoadedPositions(), sortContext.getPlayerPos(), effectiveReverse);

                if (finalComparator == null) {
                    finalComparator = modeComparator;
                } else {
                    finalComparator = finalComparator.thenComparing(modeComparator);
                }
            }
            if (finalComparator != null) {
                resultList.sort(finalComparator);
            }
        } catch (Exception e1) {
            source.sendFailure(
                    Component.translatable("createlazytick.export.sort_fallback")
            );
            mes.error("Dump sort error: "+ e1);
            try {
                resultList.sort(LazyTickSortMode.DEFAULT.getThreadSafeComparator(sortContext.getLoadedPositions(),
                        sortContext.getPlayerPos(), false));
            } catch (Exception e2) {
                RestoreFailed(e2);
            }
        }

        try {
            if (!Files.exists(dumpDir)) {
                Files.createDirectories(dumpDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "Clt_Data_dump_" + timestamp + ".txt";
            Path filePath = dumpDir.resolve(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                writer.write("=== Create Lazy Tick Data Dump ==="); writer.newLine();
                writer.write("Time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); writer.newLine();
                writer.write("Filter Chain: " + (rawFilterStr.isBlank() ? "[ALL]" : rawFilterStr)); writer.newLine();
                writer.write("Sort Chain: " + rawSortStr + " (Global Reverse: " + globalReverse + ")"); writer.newLine();
                writer.write("Total Records: " + resultList.size()); writer.newLine();
                writer.write("----------------------------------------------------------------------------------"); writer.newLine();

                writer.write(String.format("%-25s | %-30s | %-16s | %-8s | %-6s | %-7s | %s",
                        "Location", "Machine Name", "Owner", "Mode", "Val", "Loaded", "Reg.Time"));
                writer.newLine();
                writer.write("----------------------------------------------------------------------------------"); writer.newLine();

                for (Map.Entry<BlockPos, LazyTickStatCache> entry : resultList) {
                    BlockPos pos = entry.getKey();
                    LazyTickStatCache data = entry.getValue();
                    boolean isLoaded = sortContext.getLoadedPositions().contains(pos);

                    String locStr = String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
                    String modeStr = data.isForced() ? "Forced" : "Dynamic";
                    String valStr = String.valueOf(data.getScrollValue());
                    String loadStr = isLoaded ? "YES" : "NO";
                    String timeStr = String.valueOf(data.getFormattedTime());

                    String line = String.format("%-25s | %-30s | %-16s | %-8s | %-6s | %-7s | %s",
                            locStr,
                            truncate(data.getBlockId(), 29),
                            truncate(data.getOwnerName(), 15),
                            modeStr, valStr, loadStr, timeStr);

                    writer.write(line);
                    writer.newLine();
                }
            }

            MutableComponent fileComp = mes.CharM(fileName)
                    .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, fileName)));

            String path = "dumps/createlazytick/";
            source.sendSuccess(() -> Component.translatable(
                    "createlazytick.export.success_with_path",
                    path
            ).append(fileComp), true);

            return resultList.size();

        } catch (IOException e) {
            mes.error("Failed to write dump file"+ e);
            throw new SimpleCommandExceptionType(
                    Component.translatable("createlazytick.error.file_write_failed")
            ).create();
        }
    }

    private static void RestoreFailed(Exception e2) {
        mes.error("Failed to fallback to default sorting:\n"+ e2.getMessage());
    }

    private static String truncate(String s, int len) {
        if (s == null) return "null";
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);
    public static long parseDuration(String input) throws NumberFormatException {
        Matcher matcher = DURATION_PATTERN.matcher(input);
        long totalMs = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            foundAny = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            totalMs += switch (unit) {
                case "d" -> value * 24 * 60 * 60 * 1000L;
                case "h" -> value * 60 * 60 * 1000L;
                case "m" -> value * 60 * 1000L;
                case "s" -> value * 1000L;
                default -> 0;
            };
        }

        if (!foundAny) {
            throw new NumberFormatException("Invalid time format: " + input + " (Example: 3d; 12h; 30m; 3d8h6m30s)");
        }

        String leftOver = matcher.replaceAll("");
        if (!leftOver.isBlank()) {
            throw new NumberFormatException("Duration contains invalid characters: " + leftOver);
        }

        return totalMs;
    }

    public static class SortContext {
        private final Vec3 playerPos;
        private final Set<BlockPos> loadedPositions;

        public SortContext(Vec3 playerPos, Set<BlockPos> loadedPositions) {
            this.playerPos = playerPos;
            this.loadedPositions = loadedPositions;
        }

        public Vec3 getPlayerPos() { return this.playerPos; }
        public Set<BlockPos> getLoadedPositions() { return this.loadedPositions; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SortContext that = (SortContext) o;
            return Objects.equals(playerPos, that.playerPos) &&
                    Objects.equals(loadedPositions, that.loadedPositions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerPos, loadedPositions);
        }
    }

    public static SortContext createSortContext(CommandSourceStack source, Set<BlockPos> targets) {
        ServerLevel level = source.getLevel();

        Vec3 playerPos = (source.getEntity() != null) ? source.getPosition() : null;

        Set<BlockPos> loadedPositionsSnapshot = new HashSet<>();

        LongOpenHashSet loadedChunkCache = new LongOpenHashSet();
        LongOpenHashSet unloadedChunkCache = new LongOpenHashSet();

        for (BlockPos pos : targets) {
            long chunkId = ChunkPos.asLong(pos);

            if (loadedChunkCache.contains(chunkId)) {
                loadedPositionsSnapshot.add(pos);
                continue;
            }
            if (unloadedChunkCache.contains(chunkId)) {
                continue;
            }

            if (level.hasChunk(ChunkPos.getX(chunkId), ChunkPos.getZ(chunkId))) {
                loadedChunkCache.add(chunkId);
                loadedPositionsSnapshot.add(pos);
            } else {
                unloadedChunkCache.add(chunkId);
            }
        }

        return new SortContext(playerPos, loadedPositionsSnapshot);
    }

    public static void renderAllAndCleanData(
            CommandSourceStack source, ServerLevel level, List<Map.Entry<BlockPos, LazyTickStatCache>> sortedEntries,
            int page, String sortStr, boolean globalReverse, String filterStr
    ) {

        int totalMachines = sortedEntries.size();
        int totalPages = (int) Math.ceil((double) totalMachines / PAGE_SIZE);

        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalMachines);

        LazyTickListRenderer.renderHeader(source, page, totalPages, totalMachines, sortStr);

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<BlockPos, LazyTickStatCache> entry = sortedEntries.get(i);
            BlockPos pos = entry.getKey();

            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);

                if (!(be instanceof ISmartBlockEntityControl control) || control.lazytick$isDefaultState()) {
                    ForcedActiveManager.unregister(level, pos);

                    CreateLazyTick.LOGGER.debug("Cleared invalid lazytick data entries:{}",  pos.toShortString());

                    continue;
                }
            }

            LazyTickListRenderer.renderItem(source, i + 1, entry, level.isLoaded(pos));
        }

        LazyTickListRenderer.renderNavBar(source, page, totalPages, sortStr, globalReverse, filterStr);
    }

    public static class DimensionCache {
        private Set<String> machineNames = new HashSet<>();
        private Set<String> machineOwners = new HashSet<>();
        private long lastUpdateTime = 0;

        public Set<String> getMachineNames() {return machineNames; }

        public Set<String> getMachineOwners() { return machineOwners; }

        public long getLastUpdateTime() { return lastUpdateTime; }
    }

    public static final Map<ResourceKey<Level>, DimensionCache> dimensionCaches = new ConcurrentHashMap<>();
    public static final long CACHE_TIMEOUT = 60000; 

    public static DimensionCache getDimensionMachineStatCache(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        DimensionCache cache = dimensionCaches.get(dimension);
        long now = System.currentTimeMillis();

        if (cache == null || now - cache.lastUpdateTime > CACHE_TIMEOUT) {
            cache = new DimensionCache();

            Collection<LazyTickStatCache> machines = ForcedActiveManager.getForcedMachines(level).values();

            cache.machineNames = machines.stream()
                    .map(LazyTickStatCache::getBlockId)
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.toSet());

            cache.machineOwners = machines.stream()
                    .map(LazyTickStatCache::getOwnerName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.toSet());

            cache.lastUpdateTime = now;
            dimensionCaches.put(dimension, cache);
        }

        return cache;
    }
}