package com.jarvis.ai.neural;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Versioned model checkpoints under jarvis/models/&lt;player-uuid&gt;/.
 * Keeps metadata + weights + optimizer step, supports rollback to the
 * previous checkpoint.
 */
public final class ModelCheckpoint {
    private ModelCheckpoint() {}

    public record Meta(int version, Instant savedAt, long params, long trainSteps, float lastLoss) {}

    public static Path dirFor(Path root, String playerUuid) {
        return root.resolve("models").resolve(playerUuid);
    }

    public static void save(Path root, String playerUuid, JarvisNeuralNetwork net,
                            Optimizer optimizer, float lastLoss, String vocabHash) throws IOException {
        Path dir = dirFor(root, playerUuid);
        Files.createDirectories(dir);
        Path tmp = dir.resolve("model-latest.bin.tmp");
        Path latest = dir.resolve("model-latest.bin");
        Path prev = dir.resolve("model-previous.bin");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
            out.writeUTF("JARVISNN1");
            out.writeInt(optimizer.stepCount());
            out.writeFloat(lastLoss);
            out.writeUTF(vocabHash == null ? "" : vocabHash);
            out.writeLong(Instant.now().toEpochMilli());
            net.write(out);
        }
        if (Files.exists(latest)) {
            Files.move(latest, prev, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, latest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public record Loaded(Optimizer optimizer, float lastLoss, String vocabHash, long savedAt) {}

    public static Loaded load(Path root, String playerUuid, JarvisNeuralNetwork net,
                              Optimizer optimizer) throws IOException {
        Path latest = dirFor(root, playerUuid).resolve("model-latest.bin");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(latest))) {
            String magic = in.readUTF();
            if (!"JARVISNN1".equals(magic)) throw new IOException("Bad checkpoint magic");
            int steps = in.readInt();
            float lastLoss = in.readFloat();
            String vocabHash = in.readUTF();
            long savedAt = in.readLong();
            net.read(in);
            optimizer.setStepCount(steps);
            return new Loaded(optimizer, lastLoss, vocabHash, savedAt);
        }
    }

    public static boolean rollback(Path root, String playerUuid) throws IOException {
        Path dir = dirFor(root, playerUuid);
        Path latest = dir.resolve("model-latest.bin");
        Path prev = dir.resolve("model-previous.bin");
        if (!Files.exists(prev)) return false;
        Files.move(prev, latest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    public static boolean exists(Path root, String playerUuid) {
        return Files.exists(dirFor(root, playerUuid).resolve("model-latest.bin"));
    }
}
