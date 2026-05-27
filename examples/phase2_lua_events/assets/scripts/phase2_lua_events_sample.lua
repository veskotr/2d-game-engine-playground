local h = Engine.Events.subscribe("collision.begin", function()
end)

assert(type(h) == "number", "Expected numeric subscription handle")

local bad = Engine.Events.subscribe("collision.begin", 42)
assert(bad == nil, "Expected nil when callback is not a function")

Engine.Events.unsubscribe(h)

return h
