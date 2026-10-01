package com.portfolio.musictracker.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

/**
 * Cloudflare R2 などの S3 互換ストレージに保存する実装（{@code app.audio.storage=s3}）。
 * <p>
 * 再生は短時間だけ有効な署名付き URL にリダイレクトし、ストレージから直接配信する
 * （アプリのメモリ・帯域を使わない。Range リクエストによるシークもストレージ側が処理する）。
 */
@Component
@ConditionalOnProperty(name = "app.audio.storage", havingValue = "s3")
public class S3AudioStorage implements AudioStorage {

    /** 署名付き URL の有効期間。再生開始までに使われればよいので短めにする。 */
    private static final Duration PLAYBACK_URL_TTL = Duration.ofMinutes(15);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3AudioStorage(@Value("${app.audio.s3.endpoint}") String endpoint,
                          @Value("${app.audio.s3.region:auto}") String region,
                          @Value("${app.audio.s3.bucket}") String bucket,
                          @Value("${app.audio.s3.access-key-id}") String accessKeyId,
                          @Value("${app.audio.s3.secret-access-key}") String secretAccessKey) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        // R2 は仮想ホスト形式に対応しないエンドポイントもあるためパス形式で接続する
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                // S3 互換ストレージ向けに、新しい既定のチェックサム送信は必要な場合のみにする
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        this.bucket = bucket;
    }

    @Override
    public String store(MultipartFile file, Long songId) {
        String storedName = AudioStorage.newStoredName(file, songId);
        String contentType = MediaTypeFactory.getMediaType(storedName)
                .map(Object::toString).orElse("application/octet-stream");
        try (var in = file.getInputStream()) {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(storedName).contentType(contentType).build(),
                    RequestBody.fromInputStream(in, file.getSize()));
            return storedName;
        } catch (IOException | SdkException e) {
            throw new IllegalStateException("ファイルの保存に失敗しました", e);
        }
    }

    @Override
    public void delete(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storedName).build());
        } catch (SdkException e) {
            throw new IllegalStateException("ファイルの削除に失敗しました", e);
        }
    }

    @Override
    public Optional<URI> playbackUrl(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            throw new IllegalArgumentException("音源が登録されていません");
        }
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(PLAYBACK_URL_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(storedName).build())
                .build();
        try {
            return Optional.of(presigner.presignGetObject(request).url().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("署名付き URL の生成に失敗しました", e);
        }
    }

    /** S3 では {@link #playbackUrl(String)} で直接配信するため使わない。 */
    @Override
    public Resource load(String storedName) {
        throw new UnsupportedOperationException("S3 の音源は署名付き URL で配信します");
    }

    @PreDestroy
    void close() {
        presigner.close();
        client.close();
    }
}
