package com.thriftbite.backend.storefront;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: anyone can look at a store's page without logging in. */
@RestController
@RequestMapping("/api/stores")
public class StorePageController {

    private final StorePageService pages;

    public StorePageController(StorePageService pages) {
        this.pages = pages;
    }

    @GetMapping("/{slug}")
    public StorePageResponse page(@PathVariable String slug) {
        return pages.page(slug);
    }
}
