package com.thriftbite.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 0 removes the item from the cart. */
public record SetQuantityRequest(@Min(0) @Max(100) int quantity) {
}
