package org.example.loadbalancerratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Root path returns direct JSON (no HTML, no Whitelabel 404). */
@RestController
public class RootController {

    private final LoadBalancerController loadBalancerController;

    public RootController(LoadBalancerController loadBalancerController) {
        this.loadBalancerController = loadBalancerController;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root(
            @RequestParam(defaultValue = "demo-user") String user) {
        return loadBalancerController.balanceRequest(user);
    }
}
