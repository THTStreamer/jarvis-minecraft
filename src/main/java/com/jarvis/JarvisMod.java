package com.jarvis;

import com.jarvis.client.JarvisClient;
import com.jarvis.config.JarvisConfig;
import com.jarvis.core.JarvisService;
import com.jarvis.server.JarvisCommands;
import com.jarvis.server.JarvisPayloads;
import com.jarvis.server.NeoForgeRegistries;
import com.jarvis.server.NeoForgeWorldAccess;
import com.jarvis.server.ServerEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Jarvis mod entrypoint. Wires configs, networking, commands, events and the
 * core {@link JarvisService}. All AI state initializes lazily on first player
 * login so dedicated servers start fast.
 */
@Mod(JarvisConstants.MOD_ID)
public final class JarvisMod {
    private static final Logger LOG = LogUtils.getLogger();

    public JarvisMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, JarvisConfig.COMMON.spec, "jarvis-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, JarvisConfig.CLIENT.spec, "jarvis-client.toml");
        container.registerConfig(ModConfig.Type.SERVER, JarvisConfig.SERVER.spec, "jarvis-server.toml");

        modBus.addListener(JarvisPayloads::register);

        NeoForge.EVENT_BUS.register(ServerEvents.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            JarvisClient.init(modBus);
        }

        ServerEvents.setServiceFactory(() -> {
            JarvisService.init(
                ServerEvents.wiring(),
                NeoForgeRegistries::modInfos,
                NeoForgeRegistries.view());
            ServerEvents.finishInit();
            LOG.info("[Jarvis] Personal AI service initialized.");
        });
    }
}
