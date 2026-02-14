package com.example.s1000dviewer.render;

import java.util.Optional;

public interface RenderCache {
    Optional<RenderedDm> get(String key);

    void put(String key, RenderedDm renderedDm);
}
