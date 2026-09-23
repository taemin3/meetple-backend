package com.meetple.backend.domain.legal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChildSafetyPageController {

    @GetMapping({"/child-safety", "/child-safety/"})
    public String childSafetyPage() {
        return "forward:/child-safety/index.html";
    }
}
