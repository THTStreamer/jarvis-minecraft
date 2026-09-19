package com.jarvis.voice.svc;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Simple Voice Chat plugin entrypoint. Discovered by SVC via the
 * {@code @ForgeVoicechatPlugin} annotation; never loaded unless SVC is
 * installed (the dependency is optional). Forwards microphone packets to
 * {@link VoiceIntegration} for VAD/observation and captures the server API
 * for Jarvis speech playback.
 */
@ForgeVoicechatPlugin
public final class JarvisVoicePlugin implements VoicechatPlugin {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static volatile boolean loggedFirstMic;

    @Override
    public String getPluginId() {
        return "jarvis";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoiceIntegration.setApi(api);
        LOG.info("[Jarvis] Simple Voice Chat handshake complete: microphone link active.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        LOG.info("[Jarvis] Registering Simple Voice Chat events.");
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            try {
                VoiceIntegration.setApi(event.getVoicechat());
                LOG.info("[Jarvis] Voice chat server started; Jarvis link active.");
            } catch (Exception ignored) {}
        });
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            try {
                UUID sender = senderUuid(event);
                if (sender == null) return;
                if (!loggedFirstMic) {
                    loggedFirstMic = true;
                    LOG.info("[Jarvis] First microphone packet received from a player.");
                }
                byte[] opus = null;
                boolean whispering = false;
                try {
                    opus = event.getPacket().getOpusEncodedData();
                    whispering = event.getPacket().isWhispering();
                } catch (Exception ignored) {}
                VoiceIntegration.onMicrophone(sender, opus, whispering);
            } catch (Exception ignored) {}
        });
    }

    /** Resolve the sender's player UUID defensively across API revisions. */
    private static UUID senderUuid(MicrophonePacketEvent event) {
        try {
            Object conn = event.getSenderConnection();
            if (conn == null) return null;
            for (String name : new String[]{"getPlayerUuid", "getPlayerUUID", "getPlayerId"}) {
                try {
                    Method m = conn.getClass().getMethod(name);
                    Object v = m.invoke(conn);
                    if (v instanceof UUID u) return u;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }
}
