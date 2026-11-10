function mousePressed()
    assert(Engine.Input.isMouseButtonDown(Engine.MouseButtons.MIDDLE))
    assert(Engine.Input.isMouseButtonPressed(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonReleased(Engine.MouseButtons.MIDDLE))
    local delta = Engine.Input.getMouseDelta()
    assert(delta.x == 8 and delta.y == 10)
end
function mouseHeld()
    assert(Engine.Input.isMouseButtonDown(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonPressed(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonReleased(Engine.MouseButtons.MIDDLE))
    local delta = Engine.Input.getMouseDelta()
    assert(delta.x == 0 and delta.y == 0)
end
function mouseReleased()
    assert(not Engine.Input.isMouseButtonDown(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonPressed(Engine.MouseButtons.MIDDLE))
    assert(Engine.Input.isMouseButtonReleased(Engine.MouseButtons.MIDDLE))
end
function mouseIdle()
    assert(not Engine.Input.isMouseButtonDown(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonPressed(Engine.MouseButtons.MIDDLE))
    assert(not Engine.Input.isMouseButtonReleased(Engine.MouseButtons.MIDDLE))
end
