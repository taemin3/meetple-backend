package com.meetple.backend.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountDeletionPageController {

    @GetMapping({"/account-deletion", "/account-deletion/"})
    public String accountDeletionPage() {
        return "forward:/account-deletion/index.html";
    }
}
