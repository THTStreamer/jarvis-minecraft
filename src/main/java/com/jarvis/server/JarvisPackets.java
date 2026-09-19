package com.jarvis.server;

import com.jarvis.world.OreHit;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-bound payloads: speech text, ore highlights, guide target, UI open. */
public final class JarvisPackets {
    private JarvisPackets() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("jarvis", path);
    }

    public record SpeechPayload(String text) implements CustomPacketPayload {
        public static final Type<SpeechPayload> TYPE = new Type<>(id("speech"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpeechPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SpeechPayload::text, SpeechPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OreHighlightPayload(List<OreHit> hits, long expiresAt) implements CustomPacketPayload {
        public static final Type<OreHighlightPayload> TYPE = new Type<>(id("ore_highlight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OreHighlightPayload> CODEC =
            StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.hits().size());
                    for (OreHit h : payload.hits()) {
                        buf.writeUtf(h.blockId(), 128);
                        buf.writeInt(h.x());
                        buf.writeInt(h.y());
                        buf.writeInt(h.z());
                    }
                    buf.writeLong(payload.expiresAt());
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 64);
                    List<OreHit> hits = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        String oreId = buf.readUtf(128);
                        int x = buf.readInt();
                        int y = buf.readInt();
                        int z = buf.readInt();
                        hits.add(new OreHit(oreId, x, y, z, 0));
                    }
                    return new OreHighlightPayload(hits, buf.readLong());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GuidePayload(int x, int y, int z, String label, boolean clear) implements CustomPacketPayload {
        public static final Type<GuidePayload> TYPE = new Type<>(id("guide"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuidePayload> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GuidePayload::x,
                ByteBufCodecs.VAR_INT, GuidePayload::y,
                ByteBufCodecs.VAR_INT, GuidePayload::z,
                ByteBufCodecs.STRING_UTF8, GuidePayload::label,
                ByteBufCodecs.BOOL, GuidePayload::clear,
                GuidePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UiPayload(String screen) implements CustomPacketPayload {
        public static final Type<UiPayload> TYPE = new Type<>(id("ui"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UiPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UiPayload::screen, UiPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Portrait state: MAIN / SKILL / FAILED + pulse duration in milliseconds. */
    public record HudPayload(String mode, int pulseMillis) implements CustomPacketPayload {
        public static final Type<HudPayload> TYPE = new Type<>(id("hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, HudPayload::mode,
                ByteBufCodecs.VAR_INT, HudPayload::pulseMillis,
                HudPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SuppressWarnings("unused")
    private static void touch(ByteBuf buf) {}
}
