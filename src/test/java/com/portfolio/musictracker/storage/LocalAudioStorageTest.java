package com.portfolio.musictracker.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAudioStorageTest {

    @TempDir
    Path dir;

    @Test
    void 保存して読み出し削除できる() throws Exception {
        LocalAudioStorage storage = new LocalAudioStorage(dir.toString());
        String name = storage.store(new MockMultipartFile("f", "demo.MP3", "audio/mpeg", new byte[]{1, 2, 3}), 12L);

        assertThat(name).matches("12-[0-9a-f]{8}\\.mp3");
        assertThat(storage.load(name).getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(storage.playbackUrl(name)).isEmpty();

        storage.delete(name);
        assertThat(Files.exists(dir.resolve(name))).isFalse();
    }

    @Test
    void 許可していない拡張子は拒否する() {
        LocalAudioStorage storage = new LocalAudioStorage(dir.toString());
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("f", "evil.html", "text/html", new byte[]{1}), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空ファイルは拒否する() {
        LocalAudioStorage storage = new LocalAudioStorage(dir.toString());
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("f", "a.mp3", "audio/mpeg", new byte[0]), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 保存先の外を指すファイル名は読めない() {
        LocalAudioStorage storage = new LocalAudioStorage(dir.toString());
        assertThatThrownBy(() -> storage.load("../secret.mp3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../secret.mp3")).isInstanceOf(IllegalArgumentException.class);
    }
}
