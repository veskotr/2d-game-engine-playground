#pragma once
#include <cstdint>
#include <functional>
#include <typeindex>
#include <unordered_map>
#include <vector>

namespace sle::core {

// Opaque token returned by subscribe(). Pass to unsubscribe() to remove
// the handler. Default-constructed handle is invalid.
struct SubscriptionHandle
{
    uint32_t id = 0;
    bool valid() const { return id != 0; }
};

// Immediate-dispatch, type-safe event bus.
//
// Events are plain structs. Dispatch is synchronous — emit() calls all
// handlers before returning. Handlers may safely subscribe or unsubscribe
// from within a handler without iterator invalidation.
//
// Usage:
//   bus.subscribe<CollisionEvent>([](const CollisionEvent& e) { ... });
//   bus.emit(CollisionEvent{a, b});
class EventBus
{
public:
    // Register a handler for events of type T.
    // Returns a handle that can be passed to unsubscribe().
    template<typename T>
    SubscriptionHandle subscribe(std::function<void(const T&)> handler)
    {
        const uint32_t id = nextID++;
        auto wrapper = [h = std::move(handler)](const void* ptr)
        {
            h(*static_cast<const T*>(ptr));
        };
        slots[std::type_index(typeid(T))].push_back({id, std::move(wrapper)});
        handleToType.emplace(id, std::type_index(typeid(T)));
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

    // Dispatch an event to all current subscribers of type T.
    // Handlers are called in subscription order. A snapshot of the handler
    // list is taken before dispatch so that subscribe/unsubscribe calls
    // inside a handler are safe.
    template<typename T>
    void emit(const T& event)
    {
        auto it = slots.find(std::type_index(typeid(T)));
        if (it == slots.end())
            return;

        // Snapshot — safe against handler-side subscribe/unsubscribe.
        const auto snapshot = it->second;
        for (const auto& slot : snapshot)
            slot.fn(static_cast<const void*>(&event));
    }

    // Remove all subscriptions.
    void clear()
    {
        slots.clear();
        handleToType.clear();
    }

private:
    struct Slot
    {
        uint32_t id;
        std::function<void(const void*)> fn;
    };

    uint32_t nextID = 1;
    std::unordered_map<std::type_index, std::vector<Slot>> slots;
    std::unordered_map<uint32_t, std::type_index>          handleToType;
};

} // namespace sle::core
