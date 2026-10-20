package com.onlinejudge.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerCodeExecutorTarTest {

    @TempDir
    Path tempDir;

    @Test
    void streamsWorkspaceAsPortableTarArchive() throws Exception {
        Files.writeString(tempDir.resolve("solution.cpp"), "int main() { return 0; }\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("stdin.txt"), "7\n", StandardCharsets.UTF_8);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Method method = DockerCodeExecutor.class.getDeclaredMethod("streamWorkspaceArchive", Path.class, OutputStream.class);
        method.setAccessible(true);
        method.invoke(new DockerCodeExecutor(), tempDir, outputStream);

        List<TarEntry> entries = readTarEntries(outputStream.toByteArray());

        assertThat(entries).extracting(TarEntry::name).containsExactly("solution.cpp", "stdin.txt");
        assertThat(entries).extracting(TarEntry::content)
                .containsExactly("int main() { return 0; }\n", "7\n");
    }

    private List<TarEntry> readTarEntries(byte[] archive) {
        List<TarEntry> entries = new ArrayList<>();
        int offset = 0;
        while (offset + 512 <= archive.length && !isZeroBlock(archive, offset)) {
            String name = readString(archive, offset, 100);
            int size = (int) readOctal(archive, offset + 124, 12);
            int contentOffset = offset + 512;
            String content = new String(archive, contentOffset, size, StandardCharsets.UTF_8);
            entries.add(new TarEntry(name, content));
            offset = contentOffset + roundUpToBlock(size);
        }
        return entries;
    }

    private boolean isZeroBlock(byte[] archive, int offset) {
        for (int i = offset; i < offset + 512; i++) {
            if (archive[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private String readString(byte[] archive, int offset, int length) {
        int end = offset;
        while (end < offset + length && archive[end] != 0) {
            end++;
        }
        return new String(archive, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private long readOctal(byte[] archive, int offset, int length) {
        String value = readString(archive, offset, length).trim();
        return value.isBlank() ? 0 : Long.parseLong(value, 8);
    }

    private int roundUpToBlock(int size) {
        return ((size + 511) / 512) * 512;
    }

    private record TarEntry(String name, String content) {
    }
}
