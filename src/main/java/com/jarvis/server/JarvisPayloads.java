package com.jarvis.server;

import com.jarvis.world.OreHit;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration + server-side send helpers. */
public final class JarvisPayloads {
    private JarvisPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(JarvisPackets.SpeechPayload.TYPE, JarvisPackets.SpeechPayload.CODEC,
            (payload, ctx) -> handleClientSpeech(payload));
        registrar.playToClient(JarvisPackets.OreHighlightPayload.TYPE, JarvisPackets.OreHighlightPayload.CODEC,
            (payload, ctx) -> handleClientOres(payload));
        registrar.playToClient(JarvisPackets.GuidePayload.TYPE, JarvisPackets.GuidePayload.CODEC,
            (payload, ctx) -> handleClientGuide(payload));
        registrar.playToClient(JarvisPackets.UiPayload.TYPE, JarvisPackets.UiPayload.CODEC,
            (payload, ctx) -> handleClientUi(payload));
        registrar.playToClient(JarvisPackets.HudPayload.TYPE, JarvisPackets.HudPayload.CODEC,
            (payload, ctx) -> handleClientHud(payload));
    }

    // Dist-guarded: the client handler class (with client-only imports) is only
    // touched on the physical client, so dedicated servers never load it.
    private static void handleClientSpeech(JarvisPackets.SpeechPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.jarvis.client.ClientHandlers.onSpeech(payload.text());
        }
    }

    private static void handleClientOres(JarvisPackets.OreHighlightPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.jarvis.client.ClientHandlers.onOreHighlights(payload.hits(), payload.expiresAt());
        }
    }

    private static void handleClientGuide(JarvisPackets.GuidePayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.jarvis.client.ClientHandlers.onGuide(payload.x(), payload.y(), payload.z(),
                payload.label(), payload.clear());
        }
    }

    private static void handleClientUi(JarvisPackets.UiPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.jarvis.client.ClientHandlers.onUi(payload.screen());
        }
    }

    private static void handleClientHud(JarvisPackets.HudPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.jarvis.client.ClientHandlers.onHud(payload.mode(), payload.pulseMillis());
        }
    }

    public static void sendSpeech(ServerPlayer player, String text) {
        try {
            PacketDistributor.sendToPlayer(player, new JarvisPackets.SpeechPayload(text));
        } catch (Exception ignored) {}
    }

    public static void sendOreHighlights(ServerPlayer player, List<OreHit> hits) {
        try {
            long expires = System.currentTimeMillis() + 30_000L;
            PacketDistributor.sendToPlayer(player, new JarvisPackets.OreHighlightPayload(hits, expires));
        } catch (Exception ignored) {}
    }

    public static void sendGuide(ServerPlayer player, int x, int y, int z, String label, boolean clear) {
        try {
            PacketDistributor.sendToPlayer(player, new JarvisPackets.GuidePayload(x, y, z, label, clear));
        } catch (Exception ignored) {}
    }

    public static void sendUi(ServerPlayer player, String screen) {
        try {
            PacketDistributor.sendToPlayer(player, new JarvisPackets.UiPayload(screen));
        } catch (Exception ignored) {}
    }

    public static void sendHud(ServerPlayer player, String mode, int pulseMillis) {
        try {
            PacketDistributor.sendToPlayer(player,
                new JarvisPackets.HudPayload(mode, Math.max(0, pulseMillis)));
        } catch (Exception ignored) {}
    }
}
