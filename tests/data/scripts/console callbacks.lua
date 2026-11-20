return {
    init = function()
        Engine.Events.subscribe('console.probe', function() error('event failure') end)
    end,
    update = function()
        error('update failure')
    end
}
