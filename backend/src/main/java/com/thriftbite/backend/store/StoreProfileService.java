package com.thriftbite.backend.store;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.live.LiveChange.StoreChanged;
import com.thriftbite.backend.store.dto.StoreHoursEntry;
import com.thriftbite.backend.store.dto.StoreProfileRequest;
import com.thriftbite.backend.store.dto.StoreResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** The store profile: what the owner tells buyers about the store. */
@Service
public class StoreProfileService {

    private final StoreRepository stores;
    private final StoreService storeService;
    private final ApplicationEventPublisher live;

    public StoreProfileService(StoreRepository stores, StoreService storeService, ApplicationEventPublisher live) {
        this.stores = stores;
        this.storeService = storeService;
        this.live = live;
    }

    @Transactional(readOnly = true)
    public StoreResponse mine(UUID userId) {
        return StoreResponse.from(ownStore(userId));
    }

    @Transactional
    public StoreResponse update(UUID userId, StoreProfileRequest request) {
        Store store = ownStore(userId);
        if (store.getVersion() != request.version()) {
            throw new ConflictException("The store profile changed since you loaded it. Reload and try again.");
        }
        if (request.defaultPickupStart() != null && request.defaultPickupEnd() != null
                && !request.defaultPickupStart().isBefore(request.defaultPickupEnd())) {
            throw new BadRequestException("The usual pickup time has to start before it ends");
        }
        store.updateProfile(request.name().trim(), clean(request.address()), clean(request.barangay()),
                clean(request.landmark()), request.gcashAccepted(),
                request.defaultPickupStart(), request.defaultPickupEnd(), hoursOf(request));
        stores.saveAndFlush(store);   // write now, so the response carries the new version
        live.publishEvent(new StoreChanged(store.getId()));   // the store's other screens reload
        return StoreResponse.from(store);
    }

    private Store ownStore(UUID userId) {
        return stores.findById(storeService.storeIdFor(userId))
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private static Map<DayOfWeek, OpenHours> hoursOf(StoreProfileRequest request) {
        Map<DayOfWeek, OpenHours> hours = new EnumMap<>(DayOfWeek.class);
        for (StoreHoursEntry entry : request.hours()) {
            if (!entry.opens().isBefore(entry.closes())) {
                throw new BadRequestException("On " + label(entry.weekday()) + ", the store has to open before it closes");
            }
            if (hours.put(entry.weekday(), new OpenHours(entry.opens(), entry.closes())) != null) {
                throw new BadRequestException(label(entry.weekday()) + " is listed twice in the opening hours");
            }
        }
        return hours;
    }

    private static String label(DayOfWeek day) {
        return day.name().charAt(0) + day.name().substring(1).toLowerCase();
    }

    /** Empty boxes on the form are saved as "not set", not as empty text. */
    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
