function runStateMachineApi(entity)
    Engine.setState(entity, "Run")
    local inRun = Engine.isState(entity, "Run")
    Engine.sendStateEvent(entity, "jump")
    local current = Engine.getState(entity)

    if inRun and current == "Run" then
        Engine.log("state_api_ok")
    else
        Engine.log("state_api_fail")
    end
end
