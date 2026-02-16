package com.s1000Dorg.viewer.render;

import java.util.Optional;

public interface RenderCache {
    Optional<RenderedDm> get(String key);

    void put(String key, RenderedDm renderedDm);
}

