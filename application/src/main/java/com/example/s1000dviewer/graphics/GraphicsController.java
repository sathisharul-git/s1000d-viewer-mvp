package com.example.s1000dviewer.graphics;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graphics")
public class GraphicsController {

    private final GraphicsService graphicsService;

    public GraphicsController(GraphicsService graphicsService) {
        this.graphicsService = graphicsService;
    }

    @GetMapping(value = "/{icnId}", produces = "image/svg+xml")
    public ResponseEntity<String> getGraphic(@PathVariable String icnId) {
        String svg = graphicsService.getSvg(icnId);
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @GetMapping("/{icnId}/hotspots")
    public List<Hotspot> getHotspots(@PathVariable String icnId) {
        return graphicsService.getHotspots(icnId);
    }
}