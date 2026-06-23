package org.example.gp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** Заглавна страница — публична */
    @GetMapping("/")
    public String landingPage() {
        return "landing";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
