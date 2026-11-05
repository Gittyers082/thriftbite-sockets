package com.thriftbite.backend.item;

import com.thriftbite.backend.common.PageResponse;
import com.thriftbite.backend.item.dto.ItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public endpoints: anyone can browse food without logging in. */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService items;

    public ItemController(ItemService items) {
        this.items = items;
    }

    @GetMapping
    public PageResponse<ItemResponse> browse(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return items.browse(q, page, size);
    }

    @GetMapping("/{itemId}")
    public ItemResponse get(@PathVariable UUID itemId) {
        return items.get(itemId);
    }
}
