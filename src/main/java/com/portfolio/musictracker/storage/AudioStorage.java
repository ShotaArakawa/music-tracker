package com.portfolio.musictracker.storage;

import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * デモ音源（オーディオファイル）の保存先。
 * <p>
 * {@code app.audio.storage} で実装を切り替える。
 * <ul>
 *     <li>{@code local}（既定）: サーバーのディスク（{@link LocalAudioStorage}）</li>
 *     <li>{@code s3}: Cloudflare R2 などの S3 互換ストレージ（{@link S3AudioStorage}）</li>
 * </ul>
 * 保存したファイル名（例: {@code 12-ab12cd34.mp3}）を Song に記録する。
 */
public interface AudioStorage {

    /** 許可する拡張子。 */
    Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a", "ogg", "aac", "flac");

    /**
     * 音声ファイルを保存し、保存したファイル名を返す。
     *
     * @param file   アップロードされたファイル
     * @param songId 紐づく曲のID（ファイル名の接頭辞に使う）
     */
    String store(MultipartFile file, Long songId);

    /** 保存済みファイルを削除する（存在しなくてもエラーにしない）。 */
    void delete(String storedName);

    /**
     * 再生用の一時 URL（署名付き URL など）。ストレージから直接配信できる場合に返す。
     * 空の場合は {@link #load(String)} でアプリ経由で配信する。
     */
    Optional<URI> playbackUrl(String storedName);

    /** 保存済みファイルを読み出す。見つからなければ {@link IllegalArgumentException}。 */
    Resource load(String storedName);

    /**
     * アップロードされたファイルを検証し、保存用のファイル名（{@code 曲ID-ランダム8文字.拡張子}）を作る。
     * 元のファイル名はパスに使わないため、ディレクトリトラバーサルは起こらない。
     */
    static String newStoredName(MultipartFile file, Long songId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("ファイルが選択されていません");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("対応していないファイル形式です（mp3, wav, m4a, ogg, aac, flac）");
        }
        return songId + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension.toLowerCase(Locale.ROOT);
    }
}
