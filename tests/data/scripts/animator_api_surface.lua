-- Called by integration_scripting_animator_api_surface_path.cpp
-- Exercises Engine.playAnimator, stopAnimator, pauseAnimator, resumeAnimator,
-- setAnimatorSpeed, setAnimatorTime, isAnimatorPlaying, getAnimatorTime,
-- setAnimatorTarget.

local entity = Engine.createEntity()

Engine.playAnimator(entity, "assets/animations/walk.clip")
Engine.pauseAnimator(entity)
Engine.resumeAnimator(entity)
Engine.setAnimatorSpeed(entity, 2.0)
Engine.setAnimatorTime(entity, 0.5)
local playing = Engine.isAnimatorPlaying(entity)
local t = Engine.getAnimatorTime(entity)
Engine.setAnimatorTarget(entity, "Weapon", entity)
Engine.setAnimatorFloat(entity, "speedBlend", 0.75)
local speedBlend = Engine.getAnimatorFloat(entity, "speedBlend")
Engine.stopAnimator(entity)

-- Verify the spy values were recorded (checked in C++)
Engine.log("animator_api_ok")
