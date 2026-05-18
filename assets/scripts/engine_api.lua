---@meta
-- Auto-generated from C++ Lua bindings. Do not edit manually.

---@class Vec2
---@field x number
---@field y number

---@class PhysicsRaycastHit
---@field entityId integer
---@field point Vec2
---@field normal Vec2
---@field fraction number

Engine = Engine or {}

---@param ... any
---@return any
function Engine.createEntity(...) end

---@param ... any
---@return any
function Engine.destroyChildren(...) end

---@param ... any
---@return any
function Engine.destroyEntity(...) end

---@param ... any
---@return any
function Engine.error(...) end

---@param ... any
---@return any
function Engine.getChildCount(...) end

---@param ... any
---@return any
function Engine.getCurrentSceneName(...) end

---@param ... any
---@return any
function Engine.getDeltaTime(...) end

---@param ... any
---@return any
function Engine.getParent(...) end

---@param ... any
---@return any
function Engine.getTransformPosition(...) end

---@param ... any
---@return any
function Engine.getTransformScale(...) end

---@param ... any
---@return any
function Engine.getWindowSize(...) end

---@param ... any
---@return any
function Engine.hasScene(...) end

---@param ... any
---@return any
function Engine.isEntityAlive(...) end

---@param ... any
---@return any
function Engine.loadTexture(...) end

---@param ... any
---@return any
function Engine.log(...) end

---@param ... any
---@return any
function Engine.setParent(...) end

---@param ... any
---@return any
function Engine.setSpriteTexture(...) end

---@param ... any
---@return any
function Engine.setTransformPosition(...) end

---@param ... any
---@return any
function Engine.switchScene(...) end

---@param ... any
---@return any
function Engine.warn(...) end

Engine.Physics = Engine.Physics or {}

---@param ... any
---@return any
function Engine.Physics.addForce(...) end

---@param ... any
---@return any
function Engine.Physics.addImpulse(...) end

---@param ... any
---@return any
function Engine.Physics.getAngularVelocity(...) end

---@param ... any
---@return any
function Engine.Physics.getVelocity(...) end

---@param ... any
---@return any
function Engine.Physics.isDebugEnabled(...) end

---@param ... any
---@return any
function Engine.Physics.isTouching(...) end

---@param ... any
---@return any
function Engine.Physics.raycastAll(...) end

---@param ... any
---@return any
function Engine.Physics.raycastFirst(...) end

---@param ... any
---@return any
function Engine.Physics.setAngularVelocity(...) end

---@param ... any
---@return any
function Engine.Physics.setDebugEnabled(...) end

---@param ... any
---@return any
function Engine.Physics.setGravityScale(...) end

---@param ... any
---@return any
function Engine.Physics.setVelocity(...) end

Engine.Input = Engine.Input or {}

---@param ... any
---@return any
function Engine.Input.getMousePosition(...) end

---@param ... any
---@return any
function Engine.Input.isKeyDown(...) end

---@param ... any
---@return any
function Engine.Input.isKeyPressed(...) end

---@param ... any
---@return any
function Engine.Input.isKeyReleased(...) end

Engine.Camera = Engine.Camera or {}

---@param ... any
---@return any
function Engine.Camera.getPosition(...) end

---@param ... any
---@return any
function Engine.Camera.getZoom(...) end

---@param ... any
---@return any
function Engine.Camera.move(...) end

---@param ... any
---@return any
function Engine.Camera.setPosition(...) end

---@param ... any
---@return any
function Engine.Camera.setZoom(...) end

Engine.Keys = Engine.Keys or {}
Engine.Keys.A = 0
Engine.Keys.C = 0
Engine.Keys.D = 0
Engine.Keys.DOWN = 0
Engine.Keys.E = 0
Engine.Keys.EIGHT = 0
Engine.Keys.ENTER = 0
Engine.Keys.ESCAPE = 0
Engine.Keys.F = 0
Engine.Keys.F3 = 0
Engine.Keys.FIVE = 0
Engine.Keys.FOUR = 0
Engine.Keys.LEFT = 0
Engine.Keys.LEFT_CONTROL = 0
Engine.Keys.LEFT_SHIFT = 0
Engine.Keys.NINE = 0
Engine.Keys.ONE = 0
Engine.Keys.Q = 0
Engine.Keys.R = 0
Engine.Keys.RIGHT = 0
Engine.Keys.RIGHT_CONTROL = 0
Engine.Keys.RIGHT_SHIFT = 0
Engine.Keys.S = 0
Engine.Keys.SEVEN = 0
Engine.Keys.SIX = 0
Engine.Keys.SPACE = 0
Engine.Keys.TAB = 0
Engine.Keys.THREE = 0
Engine.Keys.TWO = 0
Engine.Keys.UP = 0
Engine.Keys.W = 0
Engine.Keys.ZERO = 0

Engine.MouseButtons = Engine.MouseButtons or {}
Engine.MouseButtons.LEFT = 0
Engine.MouseButtons.MIDDLE = 0
Engine.MouseButtons.RIGHT = 0
