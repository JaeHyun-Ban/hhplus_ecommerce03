package com.hhplus.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "repository")
@Getter
@Setter
public class RepositoryProperties {
    private String type = "jpa"; // 기본값 설정
}
