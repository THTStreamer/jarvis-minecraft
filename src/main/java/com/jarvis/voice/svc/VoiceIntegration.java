package com.jarvis.voice.svc;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Bridge between Jarvis core and Simple Voice Chat.
 *
 * <p>Voice transport design:
 * <ul>
 *   <li>Incoming: microphone packets arrive here from {@link JarvisVoicePlugin}
 *       and are forwarded to the core {@code VoiceReception} pipeline.</li>
 *   <li>Outgoing: Jarvis speech is synthesized by the built-in engine into PCM
 *       on the <em>client</em> (see client speech payload handling), which plays
 *       through the client's audio device. When SVC is installed, the same PCM
 *       is additionally offered to nearby players via a reflective positional
 *       broadcast attempted by the server glue; text always accompanies speech
 *       so nothing is ever lost.</li>
 * </ul>
 * All SVC calls are reflection-guarded: Jarvis compiles against the stable
 * plugin/event interfaces and runs with any SVC 2.5+/2.6+ release - or none.
 */
public final class VoiceIntegration {
    private static volatile Object serverApi;
    private static volatile BiConsumer<UUID, byte[]> micHook = (id, opus) -> {};

    private VoiceIntegration() {}

    public static void setApi(Object api) {
        serverApi = api;
    }

    /** True when Simple Voice Chat is installed and initialized. */
    public static boolean available() {
        return serverApi != null;
    }

    public static Object serverApi() {
        return serverApi;
    }

    public static void setMicrophoneHook(BiConsumer<UUID, byte[]> hook) {
        micHook = hook;
    }

    public static void onMicrophone(UUID sender, byte[] opus, boolean whispering) {
        try {
            micHook.accept(sender, opus);
        } catch (Exception ignored) {}
    }
}
