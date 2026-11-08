package com.dinodread.command;

import com.dinodread.spawn.RaptorRules;
import com.dinodread.spawn.RaptorSpawner;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator commands. {@code /dinodread raptorpack} exists because flanking is pack behavior keyed to the shared
 * pack tag that only the natural (and forced) spawner assigns: raptors placed one at a time with spawn eggs are
 * lone hunters and never flank, so this command is how you raise a pack that does.
 */
public final class DinoDreadCommands {
    private DinoDreadCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dinodread")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("raptorpack")
                .executes(DinoDreadCommands::spawnRaptorPack)));
    }

    private static int spawnRaptorPack(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("raptorpack must be run by a player: the pack spawns around them."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        int placed = RaptorSpawner.forceSpawnPack(level, player, RaptorRules.fromConfig(), level.getRandom());
        if (placed == 0) {
            context.getSource().sendFailure(Component.literal("No room for a raptor pack within 8 to 16 blocks: try open ground."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Spawned a tagged velociraptor pack of " + placed
            + " (they flank: spawn-egg raptors never do)."), true);
        return placed;
    }
}
