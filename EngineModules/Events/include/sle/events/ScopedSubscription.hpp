#pragma once
#include <sle/events/EventBus.hpp>
#include <utility>

namespace sle::events {

// Move-only RAII wrapper around a subscription handle.
// Automatically unsubscribes when destroyed or reset.
// 
// Usage:
//   auto sub = bus.subscribe<CollisionEvent>([](const CollisionEvent& e) { ... });
//   sle::events::ScopedSubscription scoped(&bus, sub);
//   // Automatically unsubscribes when scoped goes out of scope
class ScopedSubscription
{
public:
    ScopedSubscription() = default;

    ScopedSubscription(sle::events::EventBus* bus, sle::events::SubscriptionHandle handle)
        : bus_(bus), handle_(handle)
    {
    }

    ~ScopedSubscription() { reset(); }

    // Move semantics
    ScopedSubscription(ScopedSubscription&& other) noexcept
        : bus_(other.bus_), handle_(other.handle_)
    {
        other.bus_ = nullptr;
        other.handle_ = {};
    }

    ScopedSubscription& operator=(ScopedSubscription&& other) noexcept
    {
        reset();
        bus_ = other.bus_;
        handle_ = other.handle_;
        other.bus_ = nullptr;
        other.handle_ = {};
        return *this;
    }

    // Delete copy
    ScopedSubscription(const ScopedSubscription&) = delete;
    ScopedSubscription& operator=(const ScopedSubscription&) = delete;

    // Unsubscribe and reset
    void reset()
    {
        if (bus_ && handle_.valid())
        {
            bus_->unsubscribe(handle_);
            bus_ = nullptr;
            handle_ = {};
        }
    }

    sle::events::SubscriptionHandle get() const { return handle_; }
    bool valid() const { return handle_.valid() && bus_ != nullptr; }

    // Implicit bool conversion for idiomatic usage
    explicit operator bool() const { return valid(); }

private:
    sle::events::EventBus* bus_ = nullptr;
    sle::events::SubscriptionHandle handle_;
};

} // namespace sle::events
