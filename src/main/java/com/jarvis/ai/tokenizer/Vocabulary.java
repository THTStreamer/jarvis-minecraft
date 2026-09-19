package com.jarvis.ai.tokenizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Expandable vocabulary: word/subword tokens, Minecraft identifiers
 * (minecraft:diamond_ore), coordinates, numbers, directions. New words take
 * the next free row so the neural layout never changes.
 */
public final class Vocabulary {
    public static final int PAD = 0;
    public static final int UNK = 1;
    public static final int BOS = 2;
    public static final int EOS = 3;

    private final int maxSize;
    private final Map<String, Integer> tokenToId = new LinkedHashMap<>();
    private final List<String> idToToken = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Vocabulary(int maxSize) {
        this.maxSize = maxSize;
        add("<pad>");
        add("<unk>");
        add("<bos>");
        add("<eos>");
        // Seed with core assistant + Minecraft concept tokens so base behavior works pre-training.
        String[] seed = {
            "jarvis", "sir", "yes", "hello", "help", "find", "locate", "nearest", "near",
            "village", "settlement", "cave", "lush", "stronghold", "mansion", "structure",
            "biome", "desert", "plains", "forest", "where", "how", "many", "far", "guide",
            "take", "mark", "scan", "area", "around", "enemy", "enemies", "hostile", "mob",
            "mobs", "zombie", "skeleton", "creeper", "spider", "dangerous", "safe", "ore",
            "diamond", "iron", "gold", "coal", "redstone", "lapis", "emerald", "ancient",
            "debris", "block", "blocks", "item", "machine", "stress", "create", "spell",
            "glyph", "source", "north", "south", "east", "west", "up", "down", "above",
            "below", "behind", "left", "right", "here", "there", "base", "home", "food",
            "time", "weather", "rain", "day", "night", "thanks", "please", "can", "you",
            "what", "which", "show", "tell", "give", "make", "build", "craft", "need",
            "missing", "working", "broken", "why", "remember", "forget", "mute", "voice",
            "status", "skill", "skills", "learn", "teach", "know", "mean", "much", "using"
        };
        for (String s : seed) add(s);
    }

    private void add(String token) {
        if (!tokenToId.containsKey(token) && idToToken.size() < maxSize) {
            tokenToId.put(token, idToToken.size());
            idToToken.add(token);
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return idToToken.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int maxSize() { return maxSize; }

    public int idOf(String token) {
        lock.readLock().lock();
        try {
            return tokenToId.getOrDefault(token, UNK);
        } finally {
            lock.readLock().unlock();
        }
    }

    public String tokenOf(int id) {
        lock.readLock().lock();
        try {
            if (id < 0 || id >= idToToken.size()) return "<unk>";
            return idToToken.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Learn a new token; returns its id, or UNK if the vocabulary is full. */
    public int learn(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        lock.writeLock().lock();
        try {
            Integer existing = tokenToId.get(t);
            if (existing != null) return existing;
            if (idToToken.size() >= maxSize) return UNK;
            int id = idToToken.size();
            tokenToId.put(t, id);
            idToToken.add(t);
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> snapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(idToToken);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void restore(List<String> tokens) {
        lock.writeLock().lock();
        try {
            tokenToId.clear();
            idToToken.clear();
            for (String t : tokens) {
                if (idToToken.size() >= maxSize) break;
                if (!tokenToId.containsKey(t)) {
                    tokenToId.put(t, idToToken.size());
                    idToToken.add(t);
                }
            }
            // never lose the specials
            for (int i = 0; i < 4; i++) {
                String s = List.of("<pad>", "<unk>", "<bos>", "<eos>").get(i);
                if (!tokenToId.containsKey(s)) {
                    tokenToId.put(s, idToToken.size());
                    idToToken.add(s);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
