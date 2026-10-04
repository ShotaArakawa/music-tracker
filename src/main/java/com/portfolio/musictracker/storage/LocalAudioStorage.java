package com.portfolio.musictracker.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * サーバーのディスクに保存する実装（ローカル開発用。{@code app.audio.storage=local} または未設定）。
 * <p>
 * 保存先は {@code app.audio.upload-dir}（既定: プロジェクト直下の {@code uploaded-audio}）。
 * Render などの PaaS では再デプロイでファイルが消えるため、本番では {@link S3AudioStorage} を使う。
 */
@Component
@ConditionalOnProperty(name = "app.audio.storage", havingValue = "local", matchIfMissing = true)
public class LocalAudioStorage implements AudioStorage {

    private final Path uploadDir;

    public LocalAudioStorage(@Value("${app.audio.upload-dir:uploaded-audio}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, Long songId) {
        String storedName = AudioStorage.newStoredName(file, songId);
        try {
            Files.createDirectories(uploadDir);
            try (var in = file.getInputStream()) {
                Files.copy(in, resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
            }
            return storedName;
        } catch (IOException e) {
            throw new IllegalStateException("ファイルの保存に失敗しました", e);
        }
    }

    @Override
    public void delete(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            throw new IllegalStateException("ファイルの削除に失敗しました", e);
        }
    }

    /** ディスク上のファイルはアプリ経由で配信する。 */
    @Override
    public Optional<URI> playbackUrl(String storedName) {
        return Optional.empty();
    }

    @Override
    public Resource load(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            throw new IllegalArgumentException("音源が登録されていません");
        }
        Path target = resolve(storedName);
        if (!Files.isReadable(target)) {
            throw new IllegalArgumentException("音源ファイルが見つかりません");
        }
        return new FileSystemResource(target);
    }

    /** 保存ディレクトリ内のパスに解決する（ディレクトリトラバーサル防止）。 */
    private Path resolve(String storedName) {
        Path target = uploadDir.resolve(storedName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("不正なファイル名です");
        }
        return target;
    }
}
