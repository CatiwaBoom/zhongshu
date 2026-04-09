package org.cycle.file.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class FileHashUtilsTest {

    @Test
    void shouldCalculateMd5ForKnownContent() throws Exception {
        Path temp = Files.createTempFile("hash-test-", ".txt");
        try {
            Files.write(temp, "hello".getBytes(StandardCharsets.UTF_8));
            String md5 = FileHashUtils.md5(temp);
            Assertions.assertEquals("5d41402abc4b2a76b9719d911017c592", md5);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

