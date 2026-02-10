package com.example.s1000dviewer.admin;

import com.example.s1000dviewer.auth.DemoUserStore;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DemoUserStore demoUserStore;

    public AdminController(DemoUserStore demoUserStore) {
        this.demoUserStore = demoUserStore;
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return demoUserStore.findAll().stream()
            .map(user -> new UserSummaryResponse(user.username(), Set.copyOf(user.roles())))
            .toList();
    }
}