package com.thriftbite.backend.store;

import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StoreService {

    private final StoreRepository stores;
    private final StoreMemberRepository members;

    public StoreService(StoreRepository stores, StoreMemberRepository members) {
        this.stores = stores;
        this.members = members;
    }

    /** Creates a store with {@code owner} as its owner. Called when an owner signs up. */
    @Transactional
    public Store createForOwner(User owner, String name) {
        String cleanName = name == null || name.isBlank() ? owner.getUsername() : name.trim();
        Store store = stores.save(new Store(uniqueSlug(cleanName), cleanName));
        members.save(new StoreMember(store, owner, StoreRole.OWNER));
        return store;
    }

    /**
     * The store the signed-in account works at. Every owner action goes through
     * this, so an account can only ever touch its own store's food and pickups.
     */
    @Transactional(readOnly = true)
    public UUID storeIdFor(UUID userId) {
        return members.findByIdUserId(userId)
                .map(member -> member.getStore().getId())
                .orElseThrow(() -> new ForbiddenException("This account isn't linked to a store"));
    }

    /** "Lola Ising's Carinderia" becomes "lola-isings-carinderia", with a short suffix if that's taken. */
    String uniqueSlug(String name) {
        String base = slugify(name);
        String slug = base;
        while (stores.existsBySlug(slug)) {
            slug = base + "-" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
        }
        return slug;
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");   // é -> e
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replace("'", "").replace("’", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "store" : slug;
    }
}
