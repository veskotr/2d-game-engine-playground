---@meta
-- Generated from lua_api.json and validated C++ Lua bindings. Do not edit.

---@class Vec2
---@field x number
---@field y number

---@class PhysicsRaycastHit
---@field entityId integer
---@field point Vec2
---@field normal Vec2
---@field fraction number

---@class WorldTransform
---@field position Vec2
---@field rotation number
---@field scale Vec2

Engine = Engine or {}

Engine.Camera = Engine.Camera or {}

Engine.Components = Engine.Components or {}

Engine.Events = Engine.Events or {}

Engine.Input = Engine.Input or {}

Engine.Keys = Engine.Keys or {}

Engine.MouseButtons = Engine.MouseButtons or {}

Engine.Physics = Engine.Physics or {}

---@return Vec2
function Engine.Camera.getPosition() end

---@return number
function Engine.Camera.getZoom() end

---@param x number
---@param y number
function Engine.Camera.move(x, y) end

---@param x number
---@param y number
function Engine.Camera.setPosition(x, y) end

---@param zoom number
function Engine.Camera.setZoom(zoom) end

---@param componentName string
---@return integer[]
function Engine.Components.find(componentName) end

---@param entity integer
---@param componentName string
---@return table|nil
function Engine.Components.get(entity, componentName) end

---@param entity integer
---@param componentName string
---@return boolean
function Engine.Components.has(entity, componentName) end

---@param entity integer
---@param componentName string
---@return boolean
---@return string|nil
function Engine.Components.remove(entity, componentName) end

---@param entity integer
---@param componentName string
---@param data table
---@return boolean
---@return string|nil
function Engine.Components.set(entity, componentName, data) end

---@param eventName string
---@param payload? string
---@param sourceEntity? integer
---@return boolean
function Engine.Events.emit(eventName, payload, sourceEntity) end

---@param eventName string
---@param callback fun(event: table)
---@return integer|nil
function Engine.Events.subscribe(eventName, callback) end

---@param handle integer
function Engine.Events.unsubscribe(handle) end

---@return Vec2
function Engine.Input.getMouseDelta() end

---@return Vec2
function Engine.Input.getMousePosition() end

---@param key integer
---@return boolean
function Engine.Input.isKeyDown(key) end

---@param key integer
---@return boolean
function Engine.Input.isKeyPressed(key) end

---@param key integer
---@return boolean
function Engine.Input.isKeyReleased(key) end

---@param button integer
---@return boolean
function Engine.Input.isMouseButtonDown(button) end

---@param button integer
---@return boolean
function Engine.Input.isMouseButtonPressed(button) end

---@param button integer
---@return boolean
function Engine.Input.isMouseButtonReleased(button) end

---@param entity integer
---@param x number
---@param y number
---@return boolean
function Engine.Physics.addForce(entity, x, y) end

---@param entity integer
---@param x number
---@param y number
---@return boolean
function Engine.Physics.addImpulse(entity, x, y) end

---@param entity integer
---@return number
function Engine.Physics.getAngularVelocity(entity) end

---@param entity integer
---@return number|nil
function Engine.Physics.getGravityScale(entity) end

---@param entity integer
---@return Vec2|nil
function Engine.Physics.getVelocity(entity) end

---@return boolean
function Engine.Physics.isDebugEnabled() end

---@param entity integer
---@return boolean
function Engine.Physics.isTouching(entity) end

---@param startX number
---@param startY number
---@param endX number
---@param endY number
---@return PhysicsRaycastHit[]
function Engine.Physics.raycastAll(startX, startY, endX, endY) end

---@param startX number
---@param startY number
---@param endX number
---@param endY number
---@return PhysicsRaycastHit|nil
function Engine.Physics.raycastFirst(startX, startY, endX, endY) end

---@param entity integer
---@param velocity number
---@return boolean
function Engine.Physics.setAngularVelocity(entity, velocity) end

---@param enabled boolean
function Engine.Physics.setDebugEnabled(enabled) end

---@param entity integer
---@param scale number
---@return boolean
function Engine.Physics.setGravityScale(entity, scale) end

---@param entity integer
---@param x number
---@param y number
---@return boolean
function Engine.Physics.setVelocity(entity, x, y) end

---@return integer
function Engine.createEntity() end

---@param entity integer
---@return integer
function Engine.destroyChildren(entity) end

---@param entity integer
function Engine.destroyEntity(entity) end

---@param message string
function Engine.error(message) end

---@param key string
---@return integer|nil
function Engine.findEntityByKey(key) end

---@param entity integer
---@param name string
---@return number|nil
function Engine.getAnimatorFloat(entity, name) end

---@param entity integer
---@return number
function Engine.getAnimatorTime(entity) end

---@return string|nil
function Engine.getBehaviourKey() end

---@param entity integer
---@return integer
function Engine.getChildCount(entity) end

---@param entity integer
---@return integer[]
function Engine.getChildren(entity) end

---@return string
function Engine.getCurrentSceneName() end

---@return number
function Engine.getDeltaTime() end

---@param entity integer
---@return integer|nil
function Engine.getParent(entity) end

---@return integer|nil
function Engine.getSceneEntity() end

---@param entity integer
---@return string|nil
function Engine.getState(entity) end

---@param entity integer
---@return string|nil
function Engine.getStateMachineState(entity) end

---@param entity integer
---@return Vec2|nil
function Engine.getTransformPosition(entity) end

---@param entity integer
---@return number|nil
function Engine.getTransformRotation(entity) end

---@param entity integer
---@return Vec2|nil
function Engine.getTransformScale(entity) end

---@return Vec2
function Engine.getWindowSize() end

---@param entity integer
---@return WorldTransform|nil
function Engine.getWorldTransform(entity) end

---@param sceneName string
---@return boolean
function Engine.hasScene(sceneName) end

---@param path string
---@param key string
---@param overrides table
---@param parent integer
---@return integer|nil
---@return string|nil
function Engine.instantiatePrefab(path, key, overrides, parent) end

---@param entity integer
---@return boolean
function Engine.isAnimatorPlaying(entity) end

---@param entity integer
---@return boolean
function Engine.isEntityAlive(entity) end

---@param entity integer
---@return boolean
function Engine.isSoundPlaying(entity) end

---@param entity integer
---@param stateName string
---@return boolean
function Engine.isState(entity, stateName) end

---@param assetPath string
---@return integer
function Engine.loadTexture(assetPath) end

---@param message string
function Engine.log(message) end

---@param entity integer
---@return boolean
function Engine.pauseAnimator(entity) end

---@param entity integer
---@param assetPath string
---@return boolean
function Engine.playAnimator(entity, assetPath) end

---@param entity integer
---@param assetPath string
---@param loop? boolean
---@return boolean
function Engine.playSound(entity, assetPath, loop) end

---@param name string
---@param builderName string
---@return boolean
function Engine.registerScene(name, builderName) end

---@param name string
---@param path string
---@return boolean
function Engine.registerSceneFromFile(name, path) end

---@param entity integer
---@return boolean
function Engine.resumeAnimator(entity) end

---@param entity integer
---@param eventName string
---@return boolean
function Engine.sendStateEvent(entity, eventName) end

---@param entity integer
---@param name string
---@param value number
---@return boolean
function Engine.setAnimatorFloat(entity, name, value) end

---@param entity integer
---@param speed number
---@return boolean
function Engine.setAnimatorSpeed(entity, speed) end

---@param entity integer
---@param targetName string
---@param targetEntity? integer
---@return boolean
function Engine.setAnimatorTarget(entity, targetName, targetEntity) end

---@param entity integer
---@param timeSeconds number
---@return boolean
function Engine.setAnimatorTime(entity, timeSeconds) end

---@param child integer
---@param parent? integer
---@return boolean
function Engine.setParent(child, parent) end

---@param entity integer
---@param pitch number
---@return boolean
function Engine.setSoundPitch(entity, pitch) end

---@param entity integer
---@param volume number
---@return boolean
function Engine.setSoundVolume(entity, volume) end

---@param entity integer
---@param assetPath string
---@return boolean
function Engine.setSpriteTexture(entity, assetPath) end

---@param entity integer
---@param stateName string
---@return boolean
function Engine.setState(entity, stateName) end

---@param entity integer
---@param key string
---@param value boolean
---@return boolean
function Engine.setStateMachineBool(entity, key, value) end

---@param entity integer
---@param key string
---@return boolean
function Engine.setStateMachineTrigger(entity, key) end

---@param entity integer
---@param x number
---@param y number
---@return boolean
function Engine.setTransformPosition(entity, x, y) end

---@param entity integer
---@param rotation number
---@return boolean
function Engine.setTransformRotation(entity, rotation) end

---@param entity integer
---@param x number
---@param y number
---@return boolean
function Engine.setTransformScale(entity, x, y) end

---@param key string
---@param value string
---@return boolean
function Engine.setUIBinding(key, value) end

---@param entity integer
---@return boolean
function Engine.stopAnimator(entity) end

---@param entity integer
---@return boolean
function Engine.stopSound(entity) end

---@param sceneName string
---@return boolean
function Engine.switchScene(sceneName) end

---@param message string
function Engine.warn(message) end

Engine.Keys.A = 65
Engine.Keys.C = 67
Engine.Keys.D = 68
Engine.Keys.DOWN = 264
Engine.Keys.E = 69
Engine.Keys.EIGHT = 56
Engine.Keys.ENTER = 257
Engine.Keys.ESCAPE = 256
Engine.Keys.F = 70
Engine.Keys.F3 = 292
Engine.Keys.FIVE = 53
Engine.Keys.FOUR = 52
Engine.Keys.LEFT = 263
Engine.Keys.LEFT_CONTROL = 341
Engine.Keys.LEFT_SHIFT = 340
Engine.Keys.NINE = 57
Engine.Keys.ONE = 49
Engine.Keys.Q = 81
Engine.Keys.R = 82
Engine.Keys.RIGHT = 262
Engine.Keys.RIGHT_CONTROL = 345
Engine.Keys.RIGHT_SHIFT = 344
Engine.Keys.S = 83
Engine.Keys.SEVEN = 55
Engine.Keys.SIX = 54
Engine.Keys.SPACE = 32
Engine.Keys.TAB = 258
Engine.Keys.THREE = 51
Engine.Keys.TWO = 50
Engine.Keys.UP = 265
Engine.Keys.W = 87
Engine.Keys.ZERO = 48
Engine.MouseButtons.LEFT = 0
Engine.MouseButtons.MIDDLE = 2
Engine.MouseButtons.RIGHT = 1
