package com.onlinejudge.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(OnlineJudgeWebPaths.PUBLIC_ASSETS_PREFIX + "/**")
                .addResourceLocations("classpath:/static" + OnlineJudgeWebPaths.PUBLIC_ASSETS_PREFIX + "/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler(OnlineJudgeWebPaths.PUBLIC_PREFIX + "/**")
                .addResourceLocations("classpath:/static" + OnlineJudgeWebPaths.PUBLIC_PATH)
                .setCacheControl(CacheControl.noStore());

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler("/*.html", "/")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore());
    }
}
