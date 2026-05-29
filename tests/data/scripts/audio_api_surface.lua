-- Audio API surface test
local e = Engine.createEntity()

-- playSound
Engine.playSound(e, "assets/audio/coin.wav", false)

-- stopSound
Engine.stopSound(e)

-- setSoundVolume
Engine.setSoundVolume(e, 0.5)

-- setSoundPitch
Engine.setSoundPitch(e, 1.25)

-- isSoundPlaying
local playing = Engine.isSoundPlaying(e)
