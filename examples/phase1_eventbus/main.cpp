#include <sle/events/EventBus.hpp>

#include <iostream>
#include <string>
#include <vector>

namespace {

struct TestEvent
{
    int value = 0;
};

bool check(bool condition, const std::string& message)
{
    if (!condition)
    {
        std::cerr << "[FAIL] " << message << "\n";
        return false;
    }

    std::cout << "[PASS] " << message << "\n";
    return true;
}

bool testImmediateAndDeferredDispatch()
{
    sle::events::EventBus bus;
    int sum = 0;

    bus.subscribe<TestEvent>([&sum](const TestEvent& ev) { sum += ev.value; });

    bus.emit(TestEvent{3});
    if (!check(sum == 3, "emit dispatches immediately"))
        return false;

    bus.queue(TestEvent{4});
    if (!check(sum == 3, "queue does not dispatch before flushQueue"))
        return false;

    bus.flushQueue();
    return check(sum == 7, "flushQueue dispatches queued events");
}

bool testSnapshotSafetyDuringEmit()
{
    sle::events::EventBus bus;

    int firstCalls = 0;
    int secondCalls = 0;

    sle::events::SubscriptionHandle secondHandle;
    secondHandle = bus.subscribe<TestEvent>([&secondCalls](const TestEvent&) {
        ++secondCalls;
    });

    bus.subscribe<TestEvent>([&](const TestEvent&) {
        ++firstCalls;
        bus.unsubscribe(secondHandle);
    });

    bus.emit(TestEvent{});
    if (!check(firstCalls == 1 && secondCalls == 1, "unsubscribe during emit is snapshot-safe for current dispatch"))
        return false;

    bus.emit(TestEvent{});
    return check(firstCalls == 2 && secondCalls == 1, "unsubscribed handler is removed for future dispatches");
}

bool testQueueReentrancyBoundary()
{
    sle::events::EventBus bus;
    std::vector<int> seen;

    bus.subscribe<TestEvent>([&](const TestEvent& ev) {
        seen.push_back(ev.value);
        if (ev.value == 1)
            bus.queue(TestEvent{2});
    });

    bus.queue(TestEvent{1});
    bus.flushQueue();
    if (!check(seen.size() == 1 && seen[0] == 1, "events queued during flush are deferred to next flush"))
        return false;

    bus.flushQueue();
    return check(seen.size() == 2 && seen[1] == 2, "deferred event arrives on following flush cycle");
}

bool testClearSubscriptions()
{
    sle::events::EventBus bus;
    int calls = 0;

    bus.subscribe<TestEvent>([&calls](const TestEvent&) { ++calls; });
    bus.emit(TestEvent{});
    if (!check(calls == 1, "subscription receives event before clearSubscriptions"))
        return false;

    bus.clearSubscriptions();
    bus.emit(TestEvent{});
    return check(calls == 1, "clearSubscriptions removes all handlers");
}

} // namespace

int main()
{
    bool ok = true;
    ok = testImmediateAndDeferredDispatch() && ok;
    ok = testSnapshotSafetyDuringEmit() && ok;
    ok = testQueueReentrancyBoundary() && ok;
    ok = testClearSubscriptions() && ok;

    if (!ok)
    {
        std::cerr << "Phase 1 EventBus sample failed\n";
        return 1;
    }

    std::cout << "Phase 1 EventBus sample passed\n";
    return 0;
}
