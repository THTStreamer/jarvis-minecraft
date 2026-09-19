package com.jarvis.client;

import com.jarvis.world.OreHit;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side highlight layer: configured ores glow blue, the guide target
 * glows gold. World blocks are never modified - only translucent outlines
 * render on top.
 */
public final class HighlightRenderer {
    private static volatile List<OreHit> ores = List.of();
    private static volatile long oresExpire;
    private static volatile int[] guide;
    private static volatile String guideLabel = "";

    private HighlightRenderer() {}

    public static void setOres(List<OreHit> hits, long expiresAt) {
        ores = new ArrayList<>(hits);
        oresExpire = expiresAt;
    }

    public static void setGuide(int x, int y, int z, String label) {
        guide = new int[]{x, y, z};
        guideLabel = label;
    }

    public static void clearGuide() {
        guide = null;
        guideLabel = "";
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            List<OreHit> current = ores;
            if (!current.isEmpty() && System.currentTimeMillis() > oresExpire) {
                ores = List.of();
                current = ores;
            }
            if (current.isEmpty() && guide == null) return;
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            var bufferSource = mc.renderBuffers().bufferSource();
            var buffer = bufferSource.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            var levelRenderer = mc.levelRenderer;
            for (OreHit h : current) {
                AABB box = new AABB(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1)
                    .inflate(0.02);
                levelRenderer.renderLineBox(pose, buffer, box, 0.25f, 0.55f, 1.0f, 1.0f);
            }
            if (guide != null) {
                AABB box = new AABB(guide[0], guide[1] - 1, guide[2],
                    guide[0] + 1, guide[1] + 2, guide[2] + 1).inflate(0.05);
                levelRenderer.renderLineBox(pose, buffer, box, 1.0f, 0.8f, 0.2f, 1.0f);
            }
            pose.popPose();
            bufferSource.endBatch(RenderType.lines());
        } catch (Exception ignored) {}
    }
}
