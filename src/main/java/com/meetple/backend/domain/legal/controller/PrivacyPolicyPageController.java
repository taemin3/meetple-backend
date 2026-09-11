package com.meetple.backend.domain.legal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PrivacyPolicyPageController {

    @GetMapping({"/privacy-policy", "/privacy-policy/"})
    public String privacyPolicyPage() {
        return "forward:/privacy-policy/index.html";
    }
}
