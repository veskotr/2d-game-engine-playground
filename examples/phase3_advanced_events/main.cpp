#include <sle/events/EventBus.hpp>
#include <sle/events/CancellableEvent.hpp>

#include <iostream>
#include <string>
#include <vector>

namespace {

// Test event: can be cancelled mid-dispatch
struct TestEvent : sle::events::CancellableEvent
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

// ============================================================================
// Test: Handler Priority Ordering
// ============================================================================
bool testPriorityOrdering()
{
    sle::events::EventBus bus;
    std::vector<int> callOrder;

    // Subscribe handlers with different priorities
    bus.subscribe<TestEvent>(
        [&callOrder](const TestEvent&) { callOrder.push_back(1); },
        nullptr,
        1);  // priority 1

    bus.subscribe<TestEvent>(
        [&callOrder](const TestEvent&) { callOrder.push_back(10); },
        nullptr,
        10);  // priority 10 (should execute first)

    bus.subscribe<TestEvent>(
        [&callOrder](const TestEvent&) { callOrder.push_back(5); },
        nullptr,
        5);  // priority 5

    bus.emit(TestEvent{});

    if (!check(callOrder.size() == 3, "all three handlers executed"))
        return false;

    if (!check(callOrder[0] == 10 && callOrder[1] == 5 && callOrder[2] == 1,
        "handlers execute in priority order (highest first)"))
        return false;

    return true;
}

// ============================================================================
// Test: Event Cancellation
// ============================================================================
bool testEventCancellation()
{
    sle::events::EventBus bus;
    int handlerCount = 0;

    // High-priority handler that cancels
    bus.subscribe<TestEvent>(
        [&handlerCount](const TestEvent& evt) {
            ++handlerCount;
            const_cast<TestEvent&>(evt).cancelled = true;  // Veto further dispatch
        },
        nullptr,
        10);

    // Low-priority handler (should NOT execute because of cancellation)
    bus.subscribe<TestEvent>(
        [&handlerCount](const TestEvent&) {
            ++handlerCount;
        },
        nullptr,
        1);

    TestEvent evt;
    bus.emit(evt);

    if (!check(handlerCount == 1, "only high-priority handler executed"))
        return false;

    if (!check(evt.cancelled, "event is marked as cancelled"))
        return false;

    return true;
}

// ============================================================================
// Test: Filter Support
// ============================================================================
bool testFilterSupport()
{
    sle::events::EventBus bus;
    int matchCount = 0;
    int noMatchCount = 0;

    // Handler with filter: only executes if value >= 10
    bus.subscribe<TestEvent>(
        [&matchCount](const TestEvent&) { ++matchCount; },
        [](const TestEvent& e) { return e.value >= 10; });

    // Handler with different filter: only executes if value < 10
    bus.subscribe<TestEvent>(
        [&noMatchCount](const TestEvent&) { ++noMatchCount; },
        [](const TestEvent& e) { return e.value < 10; });

    // Emit event with value >= 10
    bus.emit(TestEvent{.value = 20});
    if (!check(matchCount == 1 && noMatchCount == 0,
        "only matching filter handler executed (value=20)"))
        return false;

    // Emit event with value < 10
    bus.emit(TestEvent{.value = 5});
    if (!check(matchCount == 1 && noMatchCount == 1,
        "only matching filter handler executed (value=5)"))
        return false;

    return true;
}

// ============================================================================
// Test: Priority + Cancellation Interaction
// ============================================================================
bool testPriorityWithCancellation()
{
    sle::events::EventBus bus;
    std::vector<int> executed;

    // High-priority: cancels if value > 50
    bus.subscribe<TestEvent>(
        [&executed](const TestEvent& evt) {
            executed.push_back(1);
            if (evt.value > 50)
                const_cast<TestEvent&>(evt).cancelled = true;
        },
        nullptr,
        100);

    // Medium-priority: always executes (if not cancelled)
    bus.subscribe<TestEvent>(
        [&executed](const TestEvent&) {
            executed.push_back(2);
        },
        nullptr,
        50);

    // Low-priority: always executes (if not cancelled)
    bus.subscribe<TestEvent>(
        [&executed](const TestEvent&) {
            executed.push_back(3);
        },
        nullptr,
        10);

    // Test 1: Event with value > 50 (should cancel)
    TestEvent evt1{.value = 100};
    bus.emit(evt1);
    if (!check(executed.size() == 1 && executed[0] == 1,
        "cancellation stops dispatch mid-priority"))
        return false;

    executed.clear();

    // Test 2: Event with value <= 50 (should not cancel)
    TestEvent evt2{.value = 30};
    bus.emit(evt2);
    if (!check(executed.size() == 3 && executed[0] == 1 && executed[1] == 2 && executed[2] == 3,
        "all handlers execute when event is not cancelled"))
        return false;

    return true;
}

// ============================================================================
// Test: Filter + Priority Interaction
// ============================================================================
bool testFilterWithPriority()
{
    sle::events::EventBus bus;
    std::vector<std::string> order;

    // High priority, filter for even values
    bus.subscribe<TestEvent>(
        [&order](const TestEvent&) { order.push_back("high_even"); },
        [](const TestEvent& e) { return e.value % 2 == 0; },
        100);

    // Medium priority, no filter
    bus.subscribe<TestEvent>(
        [&order](const TestEvent&) { order.push_back("medium"); },
        nullptr,
        50);

    // Low priority, filter for odd values
    bus.subscribe<TestEvent>(
        [&order](const TestEvent&) { order.push_back("low_odd"); },
        [](const TestEvent& e) { return e.value % 2 == 1; },
        10);

    // Test with even value
    bus.emit(TestEvent{.value = 4});
    if (!check(order.size() == 2 && order[0] == "high_even" && order[1] == "medium",
        "high_even and medium execute (high_even passes filter, low_odd filtered out)"))
        return false;

    order.clear();

    // Test with odd value
    bus.emit(TestEvent{.value = 5});
    if (!check(order.size() == 2 && order[0] == "medium" && order[1] == "low_odd",
        "medium and low_odd execute (high_even filtered out, low_odd passes filter)"))
        return false;

    return true;
}

} // namespace

int main()
{
    bool ok = true;
    ok = testPriorityOrdering() && ok;
    ok = testEventCancellation() && ok;
    ok = testFilterSupport() && ok;
    ok = testPriorityWithCancellation() && ok;
    ok = testFilterWithPriority() && ok;

    if (!ok)
    {
        std::cerr << "Phase 3 advanced events sample failed\n";
        return 1;
    }

    std::cout << "Phase 3 advanced events sample passed\n";
    return 0;
}
