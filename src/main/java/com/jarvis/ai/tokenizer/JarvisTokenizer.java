package com.jarvis.ai.tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JarvisTokenizer: word tokens with subword fallback, plus first-class
 * Minecraft identifiers (modid:path), coordinates, numbers and directions.
 */
public final class JarvisTokenizer {
    private static final Pattern IDENT = Pattern.compile("[a-z0-9_\\-]+:[a-z0-9_/\\-\\.]+");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern COORD = Pattern.compile("~?-?\\d+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}']+");

    private final Vocabulary vocabulary;

    public JarvisTokenizer(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public Vocabulary vocabulary() { return vocabulary; }

    public record Token(String text, int id, TokenKind kind) {}

    public enum TokenKind { WORD, SUBWORD, IDENT, NUMBER, COORD, DIRECTION, UNKNOWN }

    private static final List<String> DIRECTIONS = List.of(
        "north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest",
        "up", "down", "above", "below", "behind", "left", "right", "ahead");

    /** Tokenize without expanding the vocabulary. */
    public List<Token> tokenize(String text) {
        List<Token> out = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher ident = IDENT.matcher(lower);
        // collect identifier spans so dots/underscores inside them are preserved
        List<int[]> identSpans = new ArrayList<>();
        List<String> identTexts = new ArrayList<>();
        while (ident.find()) {
            identSpans.add(new int[]{ident.start(), ident.end()});
            identTexts.add(ident.group());
        }
        int cursor = 0;
        for (int i = 0; i <= identSpans.size(); i++) {
            int end = i < identSpans.size() ? identSpans.get(i)[0] : lower.length();
            tokenizePlain(lower.substring(cursor, end), out);
            if (i < identSpans.size()) {
                String id = identTexts.get(i);
                // split namespace:path into namespace + path pieces but keep full form too
                out.add(new Token(id, vocabulary.idOf(id), TokenKind.IDENT));
                for (String part : id.replace(':', ' ').replace('_', ' ').replace('/', ' ').split("\\s+")) {
                    if (!part.isEmpty()) emitWord(part, out);
                }
                cursor = identSpans.get(i)[1];
            }
        }
        return out;
    }

    private void tokenizePlain(String fragment, List<Token> out) {
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?|[\\p{L}']+|~").matcher(fragment);
        while (m.find()) {
            String piece = m.group();
            if (NUMBER.matcher(piece).matches()) {
                out.add(new Token(piece, vocabulary.idOf("<num:" + piece + ">"), TokenKind.NUMBER));
            } else if (COORD.matcher(piece).matches() && piece.startsWith("~")) {
                out.add(new Token(piece, vocabulary.idOf(piece), TokenKind.COORD));
            } else {
                emitWord(piece, out);
            }
        }
    }

    private void emitWord(String word, List<Token> out) {
        int id = vocabulary.idOf(word);
        if (id != Vocabulary.UNK) {
            TokenKind kind = DIRECTIONS.contains(word) ? TokenKind.DIRECTION : TokenKind.WORD;
            out.add(new Token(word, id, kind));
            return;
        }
        // subword fallback: split unknown words into known chunks of >=3 chars
        List<String> parts = splitSubwords(word);
        if (parts.size() <= 1) {
            out.add(new Token(word, Vocabulary.UNK, TokenKind.UNKNOWN));
        } else {
            for (String p : parts) {
                out.add(new Token(p, vocabulary.idOf(p), TokenKind.SUBWORD));
            }
        }
    }

    private List<String> splitSubwords(String word) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < word.length()) {
            int best = -1;
            for (int len = Math.min(8, word.length() - i); len >= 3; len--) {
                String cand = word.substring(i, i + len);
                if (vocabulary.idOf(cand) != Vocabulary.UNK) {
                    best = len;
                    break;
                }
            }
            if (best < 0) {
                parts.add(word.substring(i));
                break;
            }
            parts.add(word.substring(i, i + best));
            i += best;
        }
        return parts;
    }

    /** Encode to ids, learning unknown words into the vocabulary as configured. */
    public int[] encode(String text, boolean learnUnknown) {
        List<Token> tokens = tokenize(text);
        List<Integer> ids = new ArrayList<>();
        ids.add(Vocabulary.BOS);
        for (Token t : tokens) {
            int id = t.id();
            if (id == Vocabulary.UNK && learnUnknown
                    && (t.kind() == TokenKind.WORD || t.kind() == TokenKind.UNKNOWN || t.kind() == TokenKind.IDENT)) {
                String norm = t.text().toLowerCase(Locale.ROOT);
                if (norm.length() <= 48) id = vocabulary.learn(norm);
            }
            if (t.kind() == TokenKind.NUMBER) {
                // numbers share a magnitude bucket so magnitudes generalize
                id = vocabulary.learn(numberBucket(t.text()));
            }
            ids.add(id);
        }
        ids.add(Vocabulary.EOS);
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    public int[] encode(String text) {
        return encode(text, true);
    }

    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (id == Vocabulary.BOS || id == Vocabulary.EOS || id == Vocabulary.PAD) continue;
            String tok = vocabulary.tokenOf(id);
            if (sb.length() > 0) sb.append(' ');
            sb.append(tok);
        }
        return sb.toString();
    }

    private String numberBucket(String num) {
        try {
            double v = Double.parseDouble(num);
            double a = Math.abs(v);
            String mag = a < 10 ? "sml" : a < 100 ? "med" : a < 1000 ? "big" : "huge";
            return "<num:" + mag + ">";
        } catch (NumberFormatException e) {
            return "<num>";
        }
    }
}
