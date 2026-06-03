package com.portfolio.musictracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * デモ音源（オーディオファイル）の保存・削除を担うサービス。
 * <p>
 * 保存先は {@code app.audio.upload-dir}（既定: プロジェクト直下の {@code uploaded-audio}）。
 * 保存したファイル名（例: {@code 12-ab12cd.mp3}）を Song に記録する。
 */
@Service
public class AudioStorageService {

    /** 許可する拡張子。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a", "ogg", "aac", "flac");

    private final Path uploadDir;

    public AudioStorageService(@Value("${app.audio.upload-dir:uploaded-audio}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 音声ファイルを保存し、保存したファイル名を返す。
     *
     * @param file   アップロードされたファイル
     * @param songId 紐づく曲のID（ファイル名の接頭辞に使う）
     * @return 保存したファイル名
     */
    public String store(MultipartFile file, Long songId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("ファイルが選択されていません");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(original);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("対応していないファイル形式です（mp3, wav, m4a, ogg, aac, flac）");
        }

        String storedName = songId + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension.toLowerCase(Locale.ROOT);
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(storedName).normalize();
            // ディレクトリトラバーサル防止
            if (!target.startsWith(uploadDir)) {
                throw new IllegalArgumentException("不正なファイル名です");
            }
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return storedName;
        } catch (IOException e) {
            throw new UncheckedIOException("ファイルの保存に失敗しました", e);
        }
    }

    /** 保存済みファイルを削除する（存在しなくてもエラーにしない）。 */
    public void delete(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            Path target = uploadDir.resolve(storedName).normalize();
            if (target.startsWith(uploadDir)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("ファイルの削除に失敗しました", e);
        }
    }

    /** 保存ディレクトリ（静的配信の設定に使う）。 */
    public Path getUploadDir() {
        return uploadDir;
    }
}
