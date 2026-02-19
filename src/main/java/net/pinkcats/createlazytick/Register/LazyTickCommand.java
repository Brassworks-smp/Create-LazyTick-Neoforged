package net.pinkcats.createlazytick.Register;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.pinkcats.createlazytick.helper.command.CommandExecutor;
import net.pinkcats.createlazytick.helper.command.CommandHelper;
import net.pinkcats.createlazytick.helper.command.FilterParser;
import net.pinkcats.createlazytick.helper.command.LazyTickSortMode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LazyTickCommand {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[\"{]+");

    private static final List<String> FILTER_KEYS = List.of(
            "id:", "name:",
            "operator:", "player:",
            "mode:",
            "value>", "value<", "value=", "value>=", "value<=",
            "time>", "time<"
    );

    private static final List<String> SORT_MODE_IDS = Arrays.stream(LazyTickSortMode.values())
            .map(LazyTickSortMode::getId)
            .toList();

    private static final SuggestionProvider<CommandSourceStack> SORT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SORT_MODE_IDS, builder);

    private static final SuggestionProvider<CommandSourceStack> RESET_NAME_SUGGESTIONS = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        CommandHelper.DimensionCache cache = CommandHelper.getDimensionMachineStatCache(level);
        return SharedSuggestionProvider.suggest(cache.getMachineNames(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> RESET_OWNER_SUGGESTIONS = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        CommandHelper.DimensionCache cache = CommandHelper.getDimensionMachineStatCache(level);
        return SharedSuggestionProvider.suggest(cache.getMachineOwners(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("forced", "dynamic"), builder);

    private static final SuggestionProvider<CommandSourceStack> VALUE_OPERATOR_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("equals", "biggerthan", "smallerthan"), builder);

    private static final SuggestionProvider<CommandSourceStack> TIME_OPERATOR_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("olderthan", "newerthan"), builder);

    public static final SuggestionProvider<CommandSourceStack> COMPLEX_FILTER_SUGGESTIONS = (context, builder) -> {

        String fullInput = builder.getRemaining();  

        int lastCommandIndex = fullInput.lastIndexOf(',');

        String rawArg = (lastCommandIndex == -1) ? fullInput : fullInput.substring(lastCommandIndex + 1);

        String trimmedArg = rawArg.trim();
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(trimmedArg);

        int prefixLength = prefixMatcher.find() ? prefixMatcher.group().length() : 0;

        String currentArg = trimmedArg.substring(prefixLength);

        int spaceOffset = rawArg.indexOf(trimmedArg);  

        int baseOffset = ( lastCommandIndex + 1 ) + spaceOffset + prefixLength;

        if (prefixLength == 0 && lastCommandIndex == -1) {
            SuggestionsBuilder startBuilder = builder.createOffset(builder.getStart() + baseOffset);
            startBuilder.suggest("\"{");
            return startBuilder.buildFuture();
        }

        SuggestionsBuilder finalBuilder = builder.createOffset(builder.getStart() + baseOffset);

        Matcher matcher = FilterParser.TOKEN_PATTERN_FOR_SUGGESTION.matcher(currentArg);

        if (matcher.matches()) {
            String key = matcher.group(1).toLowerCase();
            String op = matcher.group(2);
            String val = matcher.group(3).toLowerCase();

            if (val.startsWith("\"")) val = val.substring(1);

            String searchVal = val.trim();

            ServerLevel level = context.getSource().getLevel();

            switch (key) {

                case "name", "id" -> {

                    CommandHelper.DimensionCache cache = CommandHelper.getDimensionMachineStatCache(level);
                    Set<String> cachedMachineNames = cache.getMachineNames();

                    for (String id : cachedMachineNames) {
                        if (id.toLowerCase().contains(searchVal)) {
                            finalBuilder.suggest(key + op + id);
                        }
                    }
                }

                case "operator", "player" -> {
                    CommandHelper.DimensionCache cache = CommandHelper.getDimensionMachineStatCache(level);
                    Set<String> cachedMachineOwners = cache.getMachineOwners();

                    for (String name : cachedMachineOwners) {
                        if (name.toLowerCase().startsWith(val)) {
                            finalBuilder.suggest(key + op + name);
                        }
                    }
                }

                case "mode" -> {
                    if (searchVal.equals("forced") || searchVal.equals("dynamic")) {

                        SuggestionsBuilder exactBuilder = builder.createOffset(builder.getStart() + baseOffset + currentArg.length());
                        exactBuilder.suggest(",");
                        exactBuilder.suggest("}\"");
                        return exactBuilder.buildFuture();
                    }
                    if ("forced".startsWith(searchVal)) finalBuilder.suggest(key + op + "forced");
                    if ("dynamic".startsWith(searchVal)) finalBuilder.suggest(key + op + "dynamic");
                }

                case "value" -> {
                    if (val.isEmpty()) finalBuilder.suggest(key + op + "50");
                }
                case "time" -> {
                    if (val.isEmpty()) {
                        finalBuilder.suggest(key + op + "3d");
                        finalBuilder.suggest(key + op + "12h");
                        finalBuilder.suggest(key + op + "5d12h8m6s");
                    }
                }
            }
        } else {

            for (String k : FILTER_KEYS) {
                if (k.startsWith(currentArg.toLowerCase())) {
                    finalBuilder.suggest(k);
                }
            }

            if (currentArg.isEmpty()) {
                if (!fullInput.trim().startsWith("{") && !fullInput.trim().startsWith("\"")) {
                    finalBuilder.suggest("\"{");
                }
            }
        }

        return finalBuilder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> COMPLEX_SORT_SUGGESTIONS = (context, builder) -> {
        String fullInput = builder.getRemaining();

        int lastCommandIndex = fullInput.lastIndexOf(',');
        String rawArg = (lastCommandIndex == -1) ? fullInput : fullInput.substring(lastCommandIndex + 1);

        String trimmedArg = rawArg.trim();
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(trimmedArg);

        int prefixLength = prefixMatcher.find() ? prefixMatcher.group().length() : 0;

        String currentArg = trimmedArg.substring(prefixLength);

        int spaceOffset = rawArg.indexOf(trimmedArg);  
        int baseOffset = lastCommandIndex + 1 + spaceOffset + prefixLength;  

        if (prefixLength == 0 && lastCommandIndex == -1) {
            SuggestionsBuilder startBuilder = builder.createOffset(builder.getStart() + baseOffset);
            startBuilder.suggest("\"{");
            for (String id : SORT_MODE_IDS) {

                if (id.toLowerCase().startsWith(currentArg.toLowerCase())) {
                    startBuilder.suggest(id);
                }
            }
            return startBuilder.buildFuture();
        }

        boolean isExactMatch = false;
        for (String id : SORT_MODE_IDS) {
            if (currentArg.equalsIgnoreCase(id) || currentArg.equalsIgnoreCase("!" + id)) {
                isExactMatch = true;
                break;
            }
        }

        SuggestionsBuilder finalBuilder;

        if (isExactMatch) {
            finalBuilder = builder.createOffset(builder.getStart() + baseOffset + currentArg.length());
            finalBuilder.suggest(",");
            finalBuilder.suggest("}\"");
        } else {
            finalBuilder = builder.createOffset(builder.getStart() + baseOffset);
            for (String id : SORT_MODE_IDS) {

                if (id.toLowerCase().startsWith(currentArg.toLowerCase())) {
                    finalBuilder.suggest(id);
                }

                if (("!" + id).toLowerCase().startsWith(currentArg.toLowerCase())) {
                    finalBuilder.suggest("!" + id);
                }
            }
        }

        if (lastCommandIndex == -1 && currentArg.isEmpty()) {
            if (!fullInput.trim().startsWith("{") && !fullInput.trim().startsWith("\"")) {
                finalBuilder.suggest("\"{");
            }
        }

        return finalBuilder.buildFuture();
    };

    public static void RegisterCLTCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("createlazytick") 
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("list") 
                        .executes(ctx -> CommandExecutor.onListSimple(ctx, 1, LazyTickSortMode.DEFAULT, false))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)) 
                                .executes(ctx -> CommandExecutor.onListSimple(ctx, IntegerArgumentType.getInteger(ctx, "page"),
                                        LazyTickSortMode.DEFAULT, false))

                                .then(Commands.argument("sort", StringArgumentType.word()).suggests(SORT_SUGGESTIONS) 
                                        .executes(ctx -> CommandExecutor.onListSimple(ctx,
                                                IntegerArgumentType.getInteger(ctx, "page"),
                                                LazyTickSortMode.byName(StringArgumentType.getString(ctx, "sort")),
                                                false))
                                        .then(Commands.argument("reverse", BoolArgumentType.bool()) 
                                                .executes(ctx -> CommandExecutor.onListSimple(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "page"),
                                                        LazyTickSortMode.byName(StringArgumentType.getString(ctx, "sort")),
                                                        BoolArgumentType.getBool(ctx, "reverse")
                                                ))
                                        ) 
                                ) 

                                .then(Commands.literal("complex")
                                        .then(Commands.argument("sort_chain", StringArgumentType.string())
                                                .suggests(COMPLEX_SORT_SUGGESTIONS)
                                                .executes(ctx -> CommandExecutor.onListComplex(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "page"),
                                                        StringArgumentType.getString(ctx, "sort_chain"),
                                                        false, 
                                                        ""     
                                                ))
                                                .then(Commands.argument("global_reverse", BoolArgumentType.bool())
                                                        .executes(ctx -> CommandExecutor.onListComplex(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "page"),
                                                                StringArgumentType.getString(ctx, "sort_chain"),
                                                                BoolArgumentType.getBool(ctx, "global_reverse"),
                                                                ""
                                                        ))
                                                        .then(Commands.argument("filter_chain", StringArgumentType.greedyString())
                                                                .suggests(COMPLEX_FILTER_SUGGESTIONS)
                                                                .executes(ctx -> CommandExecutor.onListComplex(ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "page"),
                                                                        StringArgumentType.getString(ctx, "sort_chain"),
                                                                        BoolArgumentType.getBool(ctx, "global_reverse"),
                                                                        StringArgumentType.getString(ctx, "filter_chain")
                                                                ))
                                                        ) 
                                                ) 
                                        ) 
                                ) 
                        ) 
                ) 

                .then(Commands.literal("reset") 
                        .then(Commands.literal("name")
                                .then(Commands.argument("block_name", ResourceLocationArgument.id())
                                        .suggests(RESET_NAME_SUGGESTIONS)
                                        .executes(CommandExecutor::onResetByName)
                                )
                        )
                        .then(Commands.literal("player")
                                .then(Commands.argument("player_name", StringArgumentType.string())
                                        .suggests(RESET_OWNER_SUGGESTIONS)
                                        .executes(CommandExecutor::onResetByPlayer)
                                )
                        )
                        .then(Commands.literal("mode")
                                .then(Commands.argument("mode_type", StringArgumentType.word())
                                        .suggests(MODE_SUGGESTIONS)
                                        .executes(CommandExecutor::onResetByMode)
                                )
                        )
                        .then(Commands.literal("value")
                                .then(Commands.argument("operator", StringArgumentType.word())
                                        .suggests(VALUE_OPERATOR_SUGGESTIONS)
                                        .then(Commands.argument("target_value", IntegerArgumentType.integer(0, 100))
                                                .executes(CommandExecutor::onResetByValue)
                                        )
                                )
                        )
                        .then(Commands.literal("radius")
                                .then(Commands.argument("range", IntegerArgumentType.integer(1))
                                        .executes(CommandExecutor::onResetByRadius)
                                )
                        )
                        .then(Commands.literal("time")
                                .then(Commands.argument("operator", StringArgumentType.word())
                                        .suggests(TIME_OPERATOR_SUGGESTIONS)
                                        .then(Commands.argument("duration", StringArgumentType.string()) 
                                                .executes(CommandExecutor::onResetByTime)
                                        )
                                )
                        )
                        .then(Commands.literal("complex")
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .suggests(COMPLEX_FILTER_SUGGESTIONS)
                                        .executes(CommandExecutor::onResetByComplex)
                                )
                        )
                ) 

                .then(Commands.literal("limit") 
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)) 
                                                .executes(ctx -> CommandExecutor.onLimitSet(ctx,
                                                        GameProfileArgument.getGameProfiles(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> CommandExecutor.onLimitRemove(ctx,
                                                GameProfileArgument.getGameProfiles(ctx, "player")))
                                )
                        )
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> CommandExecutor.onLimitCheck(ctx,
                                                GameProfileArgument.getGameProfiles(ctx, "player")))
                                )
                        )
                ) 

                .then(Commands.literal("dump")
                        .executes(ctx -> CommandExecutor.onDump(ctx, "default", false, ""))
                        .then(Commands.argument("sort_chain", StringArgumentType.string())
                                .suggests(COMPLEX_SORT_SUGGESTIONS)
                                .executes(ctx -> CommandExecutor.onDump(ctx,
                                        StringArgumentType.getString(ctx, "sort_chain"),
                                        false,
                                        ""))
                                .then(Commands.argument("global_reverse", BoolArgumentType.bool())
                                        .executes(ctx -> CommandExecutor.onDump(ctx,
                                                StringArgumentType.getString(ctx, "sort_chain"),
                                                BoolArgumentType.getBool(ctx, "global_reverse"),
                                                ""))
                                        .then(Commands.argument("filter_chain", StringArgumentType.greedyString())
                                                .suggests(COMPLEX_FILTER_SUGGESTIONS)
                                                .executes(ctx -> CommandExecutor.onDump(ctx,
                                                        StringArgumentType.getString(ctx, "sort_chain"),
                                                        BoolArgumentType.getBool(ctx, "global_reverse"),
                                                        StringArgumentType.getString(ctx, "filter_chain")
                                                ))
                                        ) 
                                ) 
                        ) 
                ) 
        );
    }
}