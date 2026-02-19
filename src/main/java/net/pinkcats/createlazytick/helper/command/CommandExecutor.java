package net.pinkcats.createlazytick.helper.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.pinkcats.createlazytick.helper.util.RegistriesWrapper;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.manager.ForcedActiveManager;
import net.pinkcats.createlazytick.manager.LazyTickSavedLimitList;
import net.pinkcats.createlazytick.manager.LazyTickStatCache;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class CommandExecutor {
    public static int onListSimple(CommandContext<CommandSourceStack> ctx, int page, LazyTickSortMode mode, boolean reverse) throws CommandSyntaxException {
        List<CommandHelper.SortCriterion> criteria = Collections.singletonList(
                new CommandHelper.SortCriterion(mode, false)
        );

        if (mode == null) {
            String wrongArg = StringArgumentType.getString(ctx, "sort");
            String available = "default, time, name, player, method, value, nearest, loaded";
            MutableComponent argComp = mes.CharM(wrongArg).withStyle(ChatFormatting.UNDERLINE);
            throw new SimpleCommandExceptionType(
                    Component.translatable("createlazytick.error.unknown_sort_mode", argComp, available)
            ).create();
        }

        Predicate<Map.Entry<BlockPos, LazyTickStatCache>> matchAll = entry -> true;

        return CommandHelper.executeList(ctx, page, criteria, reverse, matchAll, mode.getId(), "");
    }

    public static int onListComplex(CommandContext<CommandSourceStack> context, int page, String sortStr, boolean globalReverse, String filterStr) throws CommandSyntaxException {

        List<CommandHelper.SortCriterion> criteria = CommandHelper.parseSortString(sortStr);

        Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter = FilterParser.parse(filterStr, true);

        return CommandHelper.executeList(context, page, criteria, globalReverse, filter, sortStr, filterStr);
    }

    public static int onResetByName(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation rl = ctx.getArgument("block_name", ResourceLocation.class);
        String id = rl.toString();

        Block block = RegistriesWrapper.BLOCKS.getValue(rl);

        Component nameComponent;
        if (block != null && block != Blocks.AIR) {
            nameComponent = block.getName(); 
        } else {
            nameComponent = mes.Char(id); 
        }

        Component desc = Component.translatable(
                "createlazytick.desc.name_bracket",
                nameComponent.copy().withStyle(ChatFormatting.AQUA)
        );

        return CommandHelper.executeReset(ctx, desc,
                entry -> entry.getValue().getBlockId().equals(id));
    }

    public static int onResetByPlayer(CommandContext<CommandSourceStack> ctx) {
        String owner = StringArgumentType.getString(ctx, "player_name");
        Component desc = Component.translatable(
                "createlazytick.reset.desc.owner",
                owner);
        return CommandHelper.executeReset(ctx, desc,
                entry -> entry.getValue().getOwnerName().equals(owner));
    }

    public static int onResetByMode(CommandContext<CommandSourceStack> ctx) {
        String modeStr = StringArgumentType.getString(ctx, "mode_type");
        boolean isForced = modeStr.equalsIgnoreCase("forced");
        Component modeComp = Component.translatable(
                isForced ? "createlazytick.mode.short.forced" : "createlazytick.mode.short.dynamic");
        Component desc = Component.translatable(
                "createlazytick.reset.desc.mode",
                modeComp);
        return CommandHelper.executeReset(ctx, desc,
                entry -> entry.getValue().isForced() == isForced);
    }

    public static int onResetByValue(CommandContext<CommandSourceStack> ctx) {
        String operator = StringArgumentType.getString(ctx, "operator");
        int targetVal = IntegerArgumentType.getInteger(ctx, "target_value");

        String displaySymbol;
        BiPredicate<Integer, Integer> logic; 

        switch (operator.toLowerCase()) {
            case "biggerthan" -> {
                displaySymbol = ">";
                logic = (current, target) -> current > target;
            }
            case "smallerthan" -> {
                displaySymbol = "<";
                logic = (current, target) -> current < target;
            }
            case "equals" -> {
                displaySymbol = "=";
                logic = Integer::equals;
            }
            default -> {
                ctx.getSource().sendFailure(
                        Component.translatable("createlazytick.error.unknown_operator", operator)
                );
                return 0;
            }
        }
        Component desc = Component.translatable(
                "createlazytick.desc.value_bracket",
                displaySymbol,
                targetVal
        );
        return CommandHelper.executeReset(ctx, desc, entry -> {

            int currentVal = entry.getValue().getScrollValue();
            return logic.test(currentVal, targetVal);
        });
    }

    public static int onResetByRadius(CommandContext<CommandSourceStack> ctx) {
        int range = IntegerArgumentType.getInteger(ctx, "range");
        BlockPos center = BlockPos.containing(ctx.getSource().getPosition());
        double rangeSqr = range * range;

        Component desc = Component.translatable("createlazytick.desc.radius_block", range);
        return CommandHelper.executeReset(ctx, desc,
                entry -> entry.getKey().distToCenterSqr(center.getX(), center.getY(), center.getZ()) <= rangeSqr);
    }

    public static int onResetByTime(CommandContext<CommandSourceStack> ctx) {
        String operator = StringArgumentType.getString(ctx, "operator");
        String durationStr = StringArgumentType.getString(ctx, "duration");

        long targetDuration;
        try {
            targetDuration = CommandHelper.parseDuration(durationStr);
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(
                    Component.translatable(
                            "createlazytick.error.duration_format_with_examples",
                            mes.CharM(durationStr).withStyle(ChatFormatting.UNDERLINE)
                    )
            );
            return 0;
        }

        String displaySymbol;
        BiPredicate<Long, Long> logic;

        switch (operator.toLowerCase()) {
            case "olderthan" -> {
                displaySymbol = ">";

                logic = (age, target) -> age > target;
            }
            case "newerthan" -> {
                displaySymbol = "<";

                logic = (age, target) -> age < target;
            }
            default -> {
                ctx.getSource().sendFailure(
                        Component.translatable("createlazytick.error.time_filter_operator_only")
                );
                return 0;
            }
        }
        Component desc = Component.translatable(
                "createlazytick.desc.registered_duration",
                displaySymbol,
                durationStr
        );

        long now = System.currentTimeMillis();

        return CommandHelper.executeReset(ctx, desc, entry -> {
            long registeredTime = entry.getValue().getRegisteredTime();

            if (registeredTime <= 0) return false;  

            long age = now - registeredTime;

            return logic.test(age, targetDuration);
        });
    }

    public static int onResetByComplex(CommandContext<CommandSourceStack> ctx) {
        String query = StringArgumentType.getString(ctx, "query");
        try {

            Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter = FilterParser.parse(query);

            Component desc = Component.translatable("createlazytick.desc.composite_query", query);

            return CommandHelper.executeReset(ctx, desc, filter);

        } catch (CommandSyntaxException e) {

            ctx.getSource().sendFailure(mes.Char(e.getMessage()));
            return 0;
        }
    }

    public static int onLimitSet(CommandContext<CommandSourceStack> ctx,
                                 Collection<GameProfile> profiles, int limit) {
        ServerLevel level = ctx.getSource().getLevel();
        LazyTickSavedLimitList data = LazyTickSavedLimitList.get(level);

        MutableComponent playersComp = Component.empty();
        boolean first = true;

        for (GameProfile profile : profiles) {
            data.setLimit(profile.getId(), limit);

            if (!first) playersComp.append(mes.spaces(1));
            first = false;

            playersComp.append(mes.CharM(profile.getName()).withStyle(ChatFormatting.GOLD));
        }

        Component limitComp = (limit == -1)
                ? Component.translatable("createlazytick.quota.unlimited").withStyle(ChatFormatting.AQUA)
                : mes.CharM(String.valueOf(limit)).withStyle(ChatFormatting.AQUA);

        int count = profiles.size();

        MutableComponent successMessage = Component.translatable(
                "createlazytick.limit.set_success_multi",
                playersComp,
                count,
                limitComp
        );

        ctx.getSource().sendSuccess(() -> successMessage, true);
        return count;
    }

    public static int onLimitRemove(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles) {
        ServerLevel level = ctx.getSource().getLevel();
        LazyTickSavedLimitList data = LazyTickSavedLimitList.get(level);

        MutableComponent playersComp = Component.empty();
        boolean first = true;

        for (GameProfile profile : profiles) {
            data.removeLimit(profile.getId());

            if (!first) playersComp.append(mes.spaces(1));
            first = false;

            playersComp.append(mes.CharM(profile.getName()).withStyle(ChatFormatting.GOLD));
        }

        int count = profiles.size();

        MutableComponent successMessage = Component.translatable(
                "createlazytick.limit.remove_success_multi",
                playersComp,
                count
        );

        ctx.getSource().sendSuccess(() -> successMessage, true);
        return count;
    }

    public static int onLimitCheck(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles) {
        ServerLevel level = ctx.getSource().getLevel();
        LazyTickSavedLimitList limitData = LazyTickSavedLimitList.get(level);

        for (GameProfile profile : profiles) {

            int limit = limitData.getLimit(profile.getId());

            int used = ForcedActiveManager.getPlayerUsageCount(level, profile.getId());

            MutableComponent limitDisplay = (limit == -1)
                    ? Component.translatable("createlazytick.quota.unlimited").withStyle(ChatFormatting.GREEN)
                    : mes.CharM(String.valueOf(limit)).withStyle(ChatFormatting.AQUA);

            ChatFormatting statusColor = (limit != -1 && used >= limit) ? ChatFormatting.RED : ChatFormatting.GREEN;
            MutableComponent usedDisplay = mes.CharM(String.valueOf(used)).withStyle(statusColor);

            MutableComponent playerName = mes.CharM(profile.getName()).withStyle(ChatFormatting.GOLD);

            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "createlazytick.stats.player_quota",
                    playerName,
                    usedDisplay,
                    limitDisplay
            ), false);

        }
        return profiles.size();
    }

    public static int onDump(CommandContext<CommandSourceStack> ctx, String sortStr, boolean globalReverse, String filterStr) throws CommandSyntaxException {

        List<CommandHelper.SortCriterion> criteria = CommandHelper.parseSortString(sortStr);

        Predicate<Map.Entry<BlockPos, LazyTickStatCache>> filter = FilterParser.parse(filterStr, true);

        return CommandHelper.executeDump(ctx, criteria, globalReverse, filter, sortStr, filterStr);
    }
}