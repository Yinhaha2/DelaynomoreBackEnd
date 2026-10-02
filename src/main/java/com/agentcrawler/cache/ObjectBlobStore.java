package com.agentcrawler.cache;

import java.util.Optional;

public interface ObjectBlobStore {
    Optional<byte[]> get(String key);

    void put(String key, byte[] body);

    void delete(String key);
}
