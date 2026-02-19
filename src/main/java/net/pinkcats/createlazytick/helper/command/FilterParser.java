package net.pinkcats.createlazytick.helper.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.manager.LazyTickStatCache;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilterParser {

    public static final Pattern TOKEN_PATTERN = Pattern.compile("\"?([a-zA-Z]+)\"?\\s*(:|>=|<=|>|<|=)\\s*(\"[^\"]*\"|[^\"\\s,{}]+)");
    public static final Pattern TOKEN_PATTERN_FOR_SUGGESTION = Pattern.compile("^\"?([a-zA-Z]+)\"?\\s*(:|>=|<=|>|<|=)\\s*(.*)");

    private static final SimpleCommandExceptionType ERROR_EMPTY =
            new SimpleCommandExceptionType(
                    Component.translatable("createlazytick.error.filter_empty_hint")
            );

    public static Predicate<Map.Entry<BlockPos, LazyTickStatCache>> parse(String input) throws CommandSyntaxException {
        return parse(input, false);
    }

    public static Predicate<Map.Entry<BlockPos, LazyTickStatCache>> parse(String input, boolean allowEmpty) throws CommandSyntaxException {
        if (input == null) input = "";

        String trimmed = stripBracesAndQuotes(input);

        if (trimmed.isBlank()) {
            if (allowEmpty) {

                return entry -> true;
            } else {

                throw ERROR_EMPTY.create();
            }
        }

        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);

        Predicate<Map.Entry<BlockPos, LazyTickStatCache>> finalPredicate = entry -> true;
        boolean hasAnyValidFilter = false;
        int lastMatchEnd = 0;

        while (matcher.find()) {
            hasAnyValidFilter = true;
            String gap = trimmed.substring(lastMatchEnd, matcher.start());

            if (!gap.isBlank() && !gap.matches("[,\\s]+")) {
                throw new SimpleCommandExceptionType(
                        Component.translatable(
                                "createlazytick.error.unexpected_chars",
                                mes.CharM(gap).withStyle(ChatFormatting.UNDERLINE))
                ).create();
            }

            lastMatchEnd = matcher.end();

            String key = matcher.group(1).toLowerCase();
            String op = matcher.group(2);
            String rawVal = matcher.group(3);

            String val = stripQuotes(rawVal);

            try {
                finalPredicate = finalPredicate.and(createSingleFilter(key, op, val));
            } catch (CommandSyntaxException e) {
                throw e;
            } catch (Exception e) {
                mes.error(
                        "An unexpected error occurred while trying to parse conditions!\n Key: "+key+", Val: "+val+" "+e
                );
                throw new SimpleCommandExceptionType(
                        Component.translatable("createlazytick.error.internal_error_contact_admin")
                ).create();
            }
        }

        String remaining = trimmed.substring(lastMatchEnd).trim().replaceAll("[,\\s]+", "");
        if (!remaining.isEmpty()) {
            throw new SimpleCommandExceptionType(
                    Component.translatable(
                            "createlazytick.error.unparsed_conditions",
                            mes.CharM(remaining).withStyle(ChatFormatting.UNDERLINE)
                    )
            ).create();
        }

        if (!hasAnyValidFilter) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("createlazytick.error.filter_invalid_hint")
            ).create();
        }

        return finalPredicate;
    }

    private static Predicate<Map.Entry<BlockPos, LazyTickStatCache>> createSingleFilter(String key, String op, String val) throws CommandSyntaxException {
        switch (key) {
            case "name", "id" -> {
                return entry -> entry.getValue().getBlockId().toLowerCase().contains(val.toLowerCase());
            }
            case "operator", "player" -> {
                return entry -> entry.getValue().getOwnerName().equalsIgnoreCase(val);
            }
            case "mode" -> {
                if (val.equalsIgnoreCase("forced")) {
                    return entry -> entry.getValue().isForced();
                } else if (val.equalsIgnoreCase("dynamic")) {
                    return entry -> !entry.getValue().isForced();
                }
                throw new SimpleCommandExceptionType(
                        Component.translatable(
                                "createlazytick.error.mode_param_only_forced_dynamic",
                                mes.CharM(val).withStyle(ChatFormatting.UNDERLINE)
                        )
                ).create();
            }
            case "value" -> {
                int targetVal;
                try {
                    targetVal = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    throw new SimpleCommandExceptionType(
                            Component.translatable("createlazytick.error.value_format")
                                    .append(mes.CharM(val).withStyle(ChatFormatting.UNDERLINE))
                                    .append(mes.Char("]"))
                    ).create();
                }
                return entry -> compareInt(entry.getValue().getScrollValue(), targetVal, op);
            }
            case "time" -> {
                long targetDuration;
                try {
                    targetDuration = CommandHelper.parseDuration(val);
                } catch (NumberFormatException e) {
                    throw new SimpleCommandExceptionType(
                            Component.translatable(
                                    "createlazytick.error.time_format",
                                    mes.CharM(val).withStyle(ChatFormatting.UNDERLINE)
                            )
                    ).create();
                }
                long now = System.currentTimeMillis();
                return entry -> {
                    long registeredTime = entry.getValue().getRegisteredTime();
                    if (registeredTime <= 0) return false;
                    long age = now - registeredTime;
                    return compareLong(age, targetDuration, op);
                };
            }
        }
        throw new SimpleCommandExceptionType(
                Component.translatable(
                        "createlazytick.error.unknown_filter_key",
                        mes.CharM(key).withStyle(ChatFormatting.UNDERLINE)
                )
        ).create();

    }

    private static boolean compareInt(int a, int b, String op) {
        return ApplyCompare(a, b, op);
    }

    private static boolean compareLong(long a, long b, String op) {
        return ApplyCompare(a, b, op);
    }

    private static boolean ApplyCompare(long a, long b, String op) {
        return switch (op) {

            case ">" -> a > b;
            case "<" -> a < b;
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            case "=", ":" -> a == b;
            default -> false;
        };
    }

    public static String stripBracesAndQuotes(String input) {
        String s = input.trim();
        boolean changed = true;
        while (changed) {
            changed = false;

            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1).trim();
                changed = true;
            }

            if (s.length() >= 2 && s.startsWith("{") && s.endsWith("}")) {
                s = s.substring(1, s.length() - 1).trim();
                changed = true;
            }
        }
        return s;
    }

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

}