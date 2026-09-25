package io.unlockit.infrastructure.bft.http;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;

@ConfigMapping(prefix = "bft.reader")
public interface ReaderConfig {
    String registryUrl();
    Duration connectTimeout();
    Duration readTimeout();
    Duration operationTimeout();
    int maxBodyBytes();
    String originMap();
}
