package com.example.pkqb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 主页 Controller
 */
@Controller
public class IndexController {

    /**
     * 主页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
