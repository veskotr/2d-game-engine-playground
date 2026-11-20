return {
    properties = {
        speed = {type = "number", default = 3},
        active = {type = "boolean", default = false},
    },
    init = function(_, parameters)
        assert(parameters.speed == 7)
        assert(parameters.active == false)
        assert(parameters.removed == nil)
    end,
}
