package org.mcla.recomp;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Bounded byte reader. Offsets are committed by the exporter only after output closes. */
final class MclaLogChunk {
    final byte[] bytes;
    final long nextOffset;
    final String tail;
    final boolean reset, partialLine;

    private MclaLogChunk(byte[] bytes, long offset, String tail, boolean reset, boolean partial) {
        this.bytes = bytes;
        this.nextOffset = offset;
        this.tail = tail;
        this.reset = reset;
        this.partialLine = partial;
    }

    private static String fingerprint(RandomAccessFile input, long offset) throws Exception {
        input.seek(Math.max(0, offset - 64));
        StringBuilder text = new StringBuilder();
        for (int i = 0, n = (int) Math.min(64, offset); i < n; i++) {
            int value = input.read();
            if (value < 0) return "missing";
            text.append(Character.forDigit(value >>> 4, 16));
            text.append(Character.forDigit(value & 15, 16));
        }
        return text.toString();
    }

    static byte[] recentTail(InputStream input, long size, int limit) throws Exception {
        if (limit <= 0 || size < 0) throw new IllegalArgumentException("Invalid retention bounds");
        long skip = Math.max(0, size - limit);
        while (skip > 0) {
            long done = input.skip(skip);
            if (done == 0) { if (input.read() < 0) return new byte[0]; done = 1; }
            skip -= done;
        }
        ByteArrayOutputStream tail = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (tail.size() < limit &&
                (count = input.read(buffer, 0, Math.min(buffer.length, limit - tail.size()))) != -1) {
            if (count == 0) throw new java.io.IOException("Read made no progress");
            tail.write(buffer, 0, count);
        }
        byte[] bytes = tail.toByteArray();
        int start = 0;
        while (start < bytes.length && bytes[start++] != '\n') { }
        return Arrays.copyOfRange(bytes, start, bytes.length);
    }

    static MclaLogChunk read(File file, long offset, String previousTail, int limit) throws Exception {
        if (limit <= 0) throw new IllegalArgumentException("Positive chunk limit required");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long size = input.length();
            boolean reset = offset < 0 || offset > size ||
                    (offset > 0 && !fingerprint(input, offset).equals(previousTail));
            if (reset) offset = 0;
            int count = (int) Math.min(limit, size - offset);
            byte[] bytes = new byte[count];
            input.seek(offset);
            input.readFully(bytes);
            int complete = count;
            while (complete > 0 && bytes[complete - 1] != '\n') complete--;
            boolean partial = complete == 0 && count == limit;
            if (!partial) count = complete;
            long next = offset + count;
            return new MclaLogChunk(Arrays.copyOf(bytes, count), next,
                    fingerprint(input, next), reset, partial);
        }
    }
}
