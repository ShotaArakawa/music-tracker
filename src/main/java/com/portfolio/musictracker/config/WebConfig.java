package com.portfolio.musictracker.config;

import com.portfolio.musictracker.service.AudioStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * アップロードしたデモ音源を {@code /audio/**} で配信するための設定。
 * 例: 保存ファイル名が {@code 12-ab12cd.mp3} なら {@code /audio/12-ab12cd.mp3} で再生できる。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AudioStorageService audioStorageService;

    public WebConfig(AudioStorageService audioStorageService) {
        this.audioStorageService = audioStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = audioStorageService.getUploadDir().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(location);
    }
}
