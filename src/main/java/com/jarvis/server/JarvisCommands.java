package com.jarvis.server;

import com.jarvis.core.JarvisInstance;
import com.jarvis.core.JarvisService;
import com.jarvis.util.Benchmarks;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /jarvis command tree: administration, player interaction, self-tests and
 * benchmarks. All heavy work runs async; replies hop back to the server thread.
 */
public final class JarvisCommands {
    private JarvisCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("jarvis")
            .then(Commands.literal("status").executes(JarvisCommands::status))
            .then(Commands.literal("reload").requires(op(2)).executes(JarvisCommands::reload))
            .then(Commands.literal("memory").executes(JarvisCommands::memory))
            .then(Commands.literal("skills").executes(JarvisCommands::skills))
            .then(Commands.literal("knowledge")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .executes(JarvisCommands::knowledge)))
            .then(Commands.literal("training").executes(JarvisCommands::training))
            .then(Commands.literal("reset").requires(op(2))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(JarvisCommands::reset)))
            .then(Commands.literal("debug").executes(JarvisCommands::debug))
            .then(Commands.literal("profile").executes(JarvisCommands::profile))
            .then(Commands.literal("model").executes(JarvisCommands::model))
            .then(Commands.literal("voice")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .executes(JarvisCommands::voice)))
            .then(Commands.literal("benchmark").executes(JarvisCommands::benchmark))
            .then(Commands.literal("ui").executes(JarvisCommands::ui))
            .then(Commands.literal("hud").executes(JarvisCommands::hud))
            .then(Commands.literal("ask")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(JarvisCommands::ask)))
            .then(Commands.literal("scan").executes(ctx -> quick(ctx, "Jarvis, scan the area.")))
            .then(Commands.literal("locate")
                .then(Commands.argument("request", StringArgumentType.greedyString())
                    .executes(ctx -> quick(ctx,
                        "Jarvis, find " + StringArgumentType.getString(ctx, "request") + "."))))
            .then(Commands.literal("forget").executes(JarvisCommands::forget))
            .then(Commands.literal("mute").executes(ctx -> quick(ctx, "Jarvis, mute yourself.")))
            .then(Commands.literal("unmute").executes(ctx -> quick(ctx, "Jarvis, you can speak now.")))
            .then(Commands.literal("tell")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(JarvisCommands::tell))))
            .then(Commands.literal("test")
                .then(Commands.argument("suite", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        for (String s : List.of("tokenizer", "neural", "memory", "navigation",
                                "mobs", "skills", "modlearning", "voice")) {
                            b.suggest(s);
                        }
                        return b.buildFuture();
                    })
                    .executes(JarvisCommands::test)));
        event.getDispatcher().register(root);
    }

    private static java.util.function.Predicate<CommandSourceStack> op(int level) {
        return src -> src.hasPermission(level);
    }

    private static JarvisService svc() {
        return JarvisService.get();
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        try {
            ctx.getSource().sendSystemMessage(Component.literal(text));
        } catch (Exception ignored) {}
    }

    private record PlayerCtx(ServerPlayer player, JarvisInstance jarvis) {}

    private static PlayerCtx players(CommandContext<CommandSourceStack> ctx) throws Exception {
        JarvisService service = svc();
        if (service == null) throw new IllegalStateException("Jarvis is not initialized yet.");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        JarvisInstance inst = service.getOrCreate(player.getUUID(), player.getGameProfile().getName());
        return new PlayerCtx(player, inst);
    }

    // ---- handlers ----

    private static int status(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            reply(ctx, "[Jarvis] " + "Online, " + pc.jarvis().profile().personality().address() + ". "
                + pc.jarvis().skills().size() + " skills, "
                + pc.jarvis().memory().countAll() + " memories, "
                + pc.jarvis().knowledge().size() + " facts.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        try {
            JarvisService service = svc();
            if (service == null) {
                reply(ctx, "[Jarvis] Not initialized.");
                return 0;
            }
            var settings = com.jarvis.config.JarvisConfig.snapshot();
            for (JarvisInstance inst : service.loaded()) {
                inst.setSettings(() -> settings);
            }
            service.network().setEnabled(settings.jarvisToJarvisEnabled);
            reply(ctx, "[Jarvis] Configuration reloaded.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] Reload failed: " + e.getMessage());
            return 0;
        }
    }

    private static int memory(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            var mem = pc.jarvis().memory();
            String arg = "";
            try {
                arg = ctx.getArgument("memory", String.class);
            } catch (Exception ignored) {}
            reply(ctx, "[Jarvis] Memories: " + mem.countAll() + " total.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int skills(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            StringBuilder sb = new StringBuilder("[Jarvis] Skills: ");
            boolean first = true;
            for (var s : pc.jarvis().skills().all()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(s.name()).append(" (").append(String.format("%.2f", s.confidence().value())).append(")");
            }
            reply(ctx, sb.toString());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int knowledge(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            String q = StringArgumentType.getString(ctx, "query");
            var hits = pc.jarvis().knowledge().queryObject(q);
            if (hits.isEmpty()) {
                reply(ctx, "[Jarvis] I hold no knowledge about '" + q + "' yet.");
                return 1;
            }
            StringBuilder sb = new StringBuilder("[Jarvis] Knowledge about '" + q + "': ");
            for (int i = 0; i < Math.min(5, hits.size()); i++) {
                var e = hits.get(i);
                if (i > 0) sb.append(" | ");
                sb.append(e.subject()).append(" ").append(e.relation()).append(" ").append(e.object())
                    .append(" [").append(String.format("%.2f", e.confidence().value())).append("]");
            }
            reply(ctx, sb.toString());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int training(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            reply(ctx, "[Jarvis] Training steps: " + pc.jarvis().training().steps()
                + ", queued: " + pc.jarvis().training().queued()
                + ", last loss: " + pc.jarvis().training().lastLoss());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        try {
            JarvisService service = svc();
            if (service == null) {
                reply(ctx, "[Jarvis] Not initialized.");
                return 0;
            }
            String name = StringArgumentType.getString(ctx, "player");
            ServerPlayer target = null;
            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                if (p.getGameProfile().getName().equalsIgnoreCase(name)) {
                    target = p;
                    break;
                }
            }
            UUID id = target != null ? target.getUUID() : null;
            if (id == null) {
                reply(ctx, "[Jarvis] Player '" + name + "' is not online; reset needs their UUID offline. Skipped.");
                return 0;
            }
            service.remove(id);
            service.persistence().reset(id.toString());
            reply(ctx, "[Jarvis] Reset Jarvis data for " + name + ".");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] Reset failed: " + e.getMessage());
            return 0;
        }
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            StringBuilder sb = new StringBuilder("[Jarvis] Debug: ");
            for (Map.Entry<String, Object> e : pc.jarvis().debugInfo().entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(" ");
            }
            reply(ctx, sb.toString());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int profile(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            var p = pc.jarvis().profile();
            reply(ctx, "[Jarvis] Profile: " + p.playerName() + ", relationship: " + p.relationship()
                + ", trust: " + p.trustLevel() + ", exchanges: " + p.exchanges()
                + ", address: " + p.personality().address());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int model(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            reply(ctx, "[Jarvis] Model: " + pc.jarvis().network().paramCount() + " params, vocab "
                + pc.jarvis().tokenizer().vocabulary().size() + "/"
                + pc.jarvis().tokenizer().vocabulary().maxSize()
                + ", dim " + pc.jarvis().network().dim());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int voice(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
            var vp = pc.jarvis().voice().profile();
            switch (mode) {
                case "faster" -> vp.setSpeechRate(vp.speechRate() * 0.85f);
                case "slower" -> vp.setSpeechRate(vp.speechRate() * 1.15f);
                case "higher" -> vp.setBasePitchHz(vp.basePitchHz() + 12);
                case "lower" -> vp.setBasePitchHz(vp.basePitchHz() - 12);
                case "on" -> pc.jarvis().profile().setVoiceEnabled(true);
                case "off" -> pc.jarvis().profile().setVoiceEnabled(false);
                default -> {
                    reply(ctx, "[Jarvis] Voice: rate=" + vp.speechRate() + " pitch=" + vp.basePitchHz() + "Hz");
                    return 1;
                }
            }
            pc.jarvis().save(svc().persistence());
            reply(ctx, "[Jarvis] Voice updated: rate=" + String.format("%.2f", vp.speechRate())
                + " pitch=" + Math.round(vp.basePitchHz()) + "Hz.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int benchmark(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            StringBuilder sb = new StringBuilder("[Jarvis] Benchmark: ");
            for (String line : Benchmarks.lines(pc.jarvis())) {
                if (line.startsWith("debug.")) continue;
                sb.append(line).append(" ");
            }
            reply(ctx, sb.toString());
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int ui(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            JarvisPayloads.sendUi(pc.player(), "status");
            reply(ctx, "[Jarvis] Opening interface.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int hud(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            JarvisPayloads.sendUi(pc.player(), "hud");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int ask(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            String message = StringArgumentType.getString(ctx, "message");
            var snapshot = svc().wiring().world().snapshot(pc.player().getUUID().toString());
            JarvisPayloads.sendHud(pc.player(), "MAIN", 5000);
            pc.jarvis().handleAsync(message, snapshot).thenAccept(response -> {
                var server = ctx.getSource().getServer();
                server.execute(() -> {
                    pc.player().sendSystemMessage(
                        Component.literal("[Jarvis] " + response.text()));
                    ServerEvents.sendSpeech(pc.player().getUUID(), response.text());
                });
            });
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int quick(CommandContext<CommandSourceStack> ctx, String message) {
        return askWith(ctx, message);
    }

    private static int askWith(CommandContext<CommandSourceStack> ctx, String message) {
        try {
            PlayerCtx pc = players(ctx);
            var snapshot = svc().wiring().world().snapshot(pc.player().getUUID().toString());
            JarvisPayloads.sendHud(pc.player(), "MAIN", 5000);
            pc.jarvis().handleAsync(message, snapshot).thenAccept(response -> {
                var server = ctx.getSource().getServer();
                server.execute(() -> {
                    pc.player().sendSystemMessage(Component.literal("[Jarvis] " + response.text()));
                    ServerEvents.sendSpeech(pc.player().getUUID(), response.text());
                });
            });
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            pc.jarvis().memory().forgetType(com.jarvis.memory.MemoryType.EPISODIC);
            pc.jarvis().memory().forgetType(com.jarvis.memory.MemoryType.SEMANTIC);
            pc.jarvis().save(svc().persistence());
            reply(ctx, "[Jarvis] Done. I've cleared what you asked me to forget.");
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int tell(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            String target = StringArgumentType.getString(ctx, "player");
            String message = StringArgumentType.getString(ctx, "message");
            var receipt = svc().network().send(pc.player().getUUID(),
                pc.player().getGameProfile().getName(), target, message, 0);
            reply(ctx, "[Jarvis] " + receipt.note());
            return receipt.ok() ? 1 : 0;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] " + e.getMessage());
            return 0;
        }
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerCtx pc = players(ctx);
            String suite = StringArgumentType.getString(ctx, "suite").toLowerCase();
            List<String> results = JarvisSelfTest.run(pc.jarvis(), svc().wiring().world(), suite);
            for (String line : results) {
                reply(ctx, "[Jarvis:test] " + line);
            }
            return 1;
        } catch (Exception e) {
            reply(ctx, "[Jarvis] Test failed: " + e.getMessage());
            return 0;
        }
    }

    // unused helpers retained for API clarity
    @SuppressWarnings("unused")
    private static com.mojang.brigadier.arguments.ArgumentType<Boolean> boolArg() {
        return BoolArgumentType.bool();
    }

    @SuppressWarnings("unused")
    private static com.mojang.brigadier.arguments.ArgumentType<Integer> intArg() {
        return IntegerArgumentType.integer();
    }
}
