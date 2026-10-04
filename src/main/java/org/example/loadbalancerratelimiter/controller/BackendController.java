package org.example.loadbalancerratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fake backends — in real life these would be separate machines. */
@RestController
@RequestMapping("/server")
public class BackendController {

    @GetMapping("/1")
    public String server1() {
        return "Response from Backend Server 1";
    }

    @GetMapping("/2")
    public String server2() {
        return "Response from Backend Server 2";
    }

    @GetMapping("/3")
    public String server3() {
        return "Response from Backend Server 3";
    }
}
