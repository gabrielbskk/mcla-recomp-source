package org.mcla.recomp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class TestLogChunk {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        File source = File.createTempFile("mcla-log-test", ".log");
        try {
            Files.write(source.toPath(), "first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            MclaLogChunk first = MclaLogChunk.read(source, 0, "", 6);
            check(text(first.bytes).equals("first\n") && first.nextOffset == 6);
            MclaLogChunk second = MclaLogChunk.read(source, first.nextOffset, first.tail, 100);
            check(text(second.bytes).equals("second\n") && !second.reset);
            MclaLogChunk idle = MclaLogChunk.read(source, second.nextOffset, second.tail, 100);
            check(idle.bytes.length == 0 && idle.nextOffset == second.nextOffset);
            Files.write(source.toPath(), "other\nsecond\n".getBytes(StandardCharsets.UTF_8));
            MclaLogChunk rewritten = MclaLogChunk.read(source, second.nextOffset, second.tail, 100);
            check(rewritten.reset && text(rewritten.bytes).equals("other\nsecond\n"));
            Files.write(source.toPath(), "x\n".getBytes(StandardCharsets.UTF_8));
            check(MclaLogChunk.read(source, second.nextOffset, second.tail, 100).reset);
            // An incomplete line waits for the writer, including a split UTF-8 character.
            Files.write(source.toPath(), "é".getBytes(StandardCharsets.UTF_8));
            check(MclaLogChunk.read(source, 0, "", 100).bytes.length == 0);
            Files.write(source.toPath(), "é\n".getBytes(StandardCharsets.UTF_8));
            check(text(MclaLogChunk.read(source, 0, "", 100).bytes).equals("é\n"));
            // Oversized lines still progress with an explicit partial-line flag.
            Files.write(source.toPath(), "abcdef".getBytes(StandardCharsets.UTF_8));
            MclaLogChunk large = MclaLogChunk.read(source, 0, "", 3);
            check(large.bytes.length == 3 && large.partialLine);
            check(MclaLogChunk.read(source, -1, "", 100).reset);
            byte[] history = "old\nrecent\nlast\n".getBytes(StandardCharsets.UTF_8);
            byte[] retained = MclaLogChunk.recentTail(new java.io.ByteArrayInputStream(history),
                    history.length, 12);
            check(text(retained).equals("last\n"));
            check(retained.length <= 12);
            byte[] huge = new byte[9 * 1024 * 1024];
            java.util.Arrays.fill(huge, (byte) 'x');
            for (int i = 99; i < huge.length; i += 100) huge[i] = '\n';
            byte[] bounded = MclaLogChunk.recentTail(new java.io.ByteArrayInputStream(huge),
                    huge.length, 4 * 1024 * 1024);
            check(bounded.length <= 4 * 1024 * 1024);
            check(bounded.length + 4 * 65536 + 16384 < 8 * 1024 * 1024);
            System.out.println("PASS log deltas, idle, truncation/rewrite, UTF-8 and bounded reads");
        } finally { Files.deleteIfExists(source.toPath()); }
    }
}
