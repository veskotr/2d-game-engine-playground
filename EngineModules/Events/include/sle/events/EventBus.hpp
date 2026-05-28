#pragma once
#include <sle/events/CancellableEvent.hpp>
#include <cstdint>
#include <functional>
#include <typeindex>
#include <unordered_map>
#include <vector>
#include <algorithm>
#include <type_traits>

namespace sle::events {

// Opaque token returned by subscribe(). Pass to unsubscribe() to remove
// the handler. Default-constructed handle is invalid.
struct SubscriptionHandle
{
    uint32_t id = 0;
    bool valid() const { return id != 0; }
};

// Immediate-dispatch, type-safe event bus with advanced features.
//
// Events are plain structs. Dispatch is synchronous — emit() calls all
// handlers before returning. Handlers may safely subscribe or unsubscribe
// from within a handler without iterator invalidation.
//
// Features:
// - Priority: Higher-priority handlers execute first (Phase 3)
// - Filters: Optional predicate; handler only executes if filter returns true (Phase 3)
// - Cancellation: Events inheriting CancellableEvent can be vetoed mid-dispatch (Phase 3)
// - Immediate & Deferred: emit() now, queue() for later (Phase 1)
//
// Usage:
//   bus.subscribe<CollisionEvent>([](const CollisionEvent& e) { ... }, nullptr, 10);  // priority 10
//   bus.subscribe<CollisionEvent>([](const CollisionEvent& e) { ... },                // filter
//       [](const CollisionEvent& e) { return e.entityA != e.entityB; });
//   bus.emit(CollisionEvent{a, b});
//   bus.queue(CollisionEvent{a, b});
class EventBus
{
public:
    // Register a handler for events of type T with optional filter and priority.
    // filter: Optional predicate; if provided, handler only executes when filter returns true.
    // priority: Higher values execute first. Default 0. Useful for UI (high) vs gameplay (normal).
    // Returns a handle that can be passed to unsubscribe().
    template<typename T>
    SubscriptionHandle subscribe(
        std::function<void(const T&)> handler,
        std::function<bool(const T&)> filter = nullptr,
        int priority = 0)
    {
        const uint32_t id = nextID++;
        
        auto wrapper = [h = std::move(handler), f = std::move(filter)](const void* ptr) {
            const T& event = *static_cast<const T*>(ptr);
            // If filter is set, only call handler if filter returns true
            if (f && !f(event))
                return;
            h(event);
        };
        
        auto& list = slots[std::type_index(typeid(T))];
        list.push_back({id, std::move(wrapper), priority});
        handleToType.emplace(id, std::type_index(typeid(T)));
        
        // Sort by priority descending (higher priority first)
        std::sort(list.begin(), list.end(),
            [](const Slot& a, const Slot& b) { return a.priority > b.priority; });
        
        return SubscriptionHandle{id};
    }

    // Remove a previously registered handler. Safe to call with an invalid
    // or already-removed handle (no-op).
    void unsubscribe(SubscriptionHandle handle)
    {
        if (!handle.valid())
            return;

        auto typeIt = handleToType.find(handle.id);
        if (typeIt == handleToType.end())
            return;

        auto slotIt = slots.find(typeIt->second);
        if (slotIt != slots.end())
        {
            auto& list = slotIt->second;
            list.erase(
                std::remove_if(list.begin(), list.end(),
                    [id = handle.id](const Slot& s) { return s.id == id; }),
                list.end());
        }
        handleToType.erase(typeIt);
    }

    // Dispatch an event to all current subscribers of type T in priority order.
    // Handlers are called by priority (highest first). A snapshot of the handler
    // list is taken before dispatch so that subscribe/unsubscribe calls
    // inside a handler are safe.
    // 
    // If event inherits CancellableEvent and a handler sets event.cancelled = true,
    // dispatch stops and no further handlers are called.
    template<typename T>
    void emit(const T& event)
    {
        auto it = slots.find(std::type_index(typeid(T)));
        if (it == slots.end())
            return;

        // Snapshot — safe against handler-side subscribe/unsubscribe.
        const auto snapshot = it->second;
        for (const auto& slot : snapshot)
        {
            slot.fn(static_cast<const void*>(&event));
            
            // Check cancellation (Phase 3 feature)
            if constexpr (std::is_base_of_v<CancellableEvent, T>)
            {
                if (static_cast<const CancellableEvent&>(event).cancelled)
                    break;
            }
        }
    }

    // Queue an event for deferred dispatch. Safe to call from within a handler
    // or callback. Queued events are dispatched at the start of the next frame
    // via flushQueue().
    template<typename T>
    void queue(const T& event)
    {
        pendingQueue_.push_back([this, ev = event]() {
            emit(ev);
        });
    }

    // Dispatch all queued events in FIFO order, then clear the queue.
    // Safe to call from outside handlers. Events queued during flush belong
    // to the next frame and will be dispatched on the next flushQueue() call.
    void flushQueue()
    {
        auto q = std::move(pendingQueue_);
        for (auto& fn : q)
            fn();
    }

    // Remove all subscriptions.
    void clearSubscriptions()
    {
        slots.clear();
        handleToType.clear();
    }

    // Remove all queued deferred events without dispatching them.
    void clearQueue()
    {
        pendingQueue_.clear();
    }

    // Remove both subscriptions and queued deferred events.
    void clearAll()
    {
        clearSubscriptions();
        clearQueue();
    }

private:
    struct Slot
    {
        uint32_t id;
        std::function<void(const void*)> fn;
        int priority = 0;  // Phase 3: higher values execute first
    };

    uint32_t nextID = 1;
    std::unordered_map<std::type_index, std::vector<Slot>> slots;
    std::unordered_map<uint32_t, std::type_index>          handleToType;
    std::vector<std::function<void()>>                      pendingQueue_;
};

} // namespace sle::events
