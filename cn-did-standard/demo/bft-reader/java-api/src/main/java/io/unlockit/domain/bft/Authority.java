package io.unlockit.domain.bft;

import java.util.List;

public record Authority(String dsoDid, String bootstrapUrl, List<String> advertisedEndpoints) {
    public Authority {
        advertisedEndpoints = List.copyOf(advertisedEndpoints);
    }
}
