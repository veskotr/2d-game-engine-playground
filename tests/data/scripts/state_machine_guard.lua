-- Guard function for state machine transition tests.
-- Returns true to allow the transition to proceed.
function canTransitionToRun(entity)
    return true
end

-- Guard function that blocks transitions.
function cannotTransition(entity)
    return false
end
