package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.ai.tokenizer.Vocabulary;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TokenizerTest {
    @Test
    public void roundTrip() {
        Vocabulary vocab = new Vocabulary(512);
        JarvisTokenizer tokenizer = new JarvisTokenizer(vocab);
        int[] ids = tokenizer.encode("Jarvis, find me a village!", true);
        assertTrue(ids.length > 6, "expected several tokens");
        String back = tokenizer.decode(ids);
        assertTrue(back.contains("village"), "roundtrip kept village: " + back);
    }

    @Test
    public void identifiersAndNumbers() {
        Vocabulary vocab = new Vocabulary(512);
        JarvisTokenizer tokenizer = new JarvisTokenizer(vocab);
        List<JarvisTokenizer.Token> tokens = tokenizer.tokenize("locate create:mechanical_press 486 blocks north-east");
        boolean hasIdent = tokens.stream().anyMatch(t -> t.kind() == JarvisTokenizer.TokenKind.IDENT);
        boolean hasDir = tokens.stream().anyMatch(t -> t.kind() == JarvisTokenizer.TokenKind.DIRECTION);
        boolean hasNum = tokens.stream().anyMatch(t -> t.kind() == JarvisTokenizer.TokenKind.NUMBER);
        assertTrue(hasIdent, "identifier recognized");
        assertTrue(hasDir, "direction recognized");
        assertTrue(hasNum, "number recognized");
    }

    @Test
    public void vocabularyLearns() {
        Vocabulary vocab = new Vocabulary(512);
        int before = vocab.size();
        JarvisTokenizer tokenizer = new JarvisTokenizer(vocab);
        tokenizer.encode("mechanical press stress units", true);
        assertTrue(vocab.size() > before, "unknown words learned");
        assertEquals(vocab.idOf("mechanical"), vocab.idOf("mechanical"), "stable ids");
    }

    @Test
    public void subwordFallbackKeepsUnknownReadable() {
        Vocabulary vocab = new Vocabulary(512);
        JarvisTokenizer tokenizer = new JarvisTokenizer(vocab);
        List<JarvisTokenizer.Token> tokens = tokenizer.tokenize("supercalifragilistic");
        assertTrue(!tokens.isEmpty(), "unknown word still yields tokens");
    }
}
