-- ============================================================================
--  Figura avatar script: mouth moves when you talk (Plasmo Voice)
--  Requires: DTIMC mod + Figura + Plasmo Voice (>= 2.1.1) on YOUR client.
--  Other players only need Figura to see it: the host sends the mouth state
--  to them with a Figura ping, so they do NOT need Plasmo Voice or DTIMC.
-- ============================================================================

-- ------------------------------- CONFIG -------------------------------------
-- 1) Point this at the mouth/jaw part of YOUR model (Blockbench group path).
--    Example: models.model.Head.Mouth   (model name "model", group "Head" > "Mouth")
local MOUTH = models.model.root.Head.Mouth

-- 2) How the mouth moves:
--    "rotate" = jaw swings open (pivot the group at the hinge in Blockbench)
--    "scale"  = mouth part gets taller
--    "uv"     = switch between frames stacked vertically in the texture
local MODE = "rotate"

local MAX_ROT   = 25     -- rotate: degrees when fully open
local MIN_SCALE = 0.15   -- scale : Y scale when closed (1 = fully open)
local UV_FRAMES  = 4     -- uv    : number of mouth frames in the texture
local UV_FRAME_H = 4     -- uv    : height of one frame in texture pixels

-- 3) Voice sensitivity. plasmovoice.getVoiceLevel() is 0..1 but NOT zero-based.
--    With synthetic test tones: faint room noise ~0.58, quiet speech ~0.72,
--    normal speech ~0.83, loud ~0.94. YOUR microphone will differ, so set
--    DEBUG = true, talk, and watch the number on the action bar.
--    Raise LEVEL_CLOSED if the mouth twitches in silence;
--    lower LEVEL_OPEN if it never opens wide enough.
local LEVEL_CLOSED = 0.65
local LEVEL_OPEN   = 0.88
local DEBUG = false      -- true = show the live voice level on the action bar (host only)

-- 4) Optional manual key: hold it to open the mouth (works without Plasmo Voice).
--    Set to nil to disable. Key names: https://minecraft.wiki/w/Options#Controls
local MANUAL_KEY = "key.keyboard.v"

-- 5) Smoothing (0..1 per tick, higher = snappier)
local ATTACK  = 0.6   -- opening
local RELEASE = 0.3   -- closing
-- ----------------------------------------------------------------------------

local STEPS = 4        -- mouth states sent over the network (0..STEPS) - keeps pings tiny

local target   = 0     -- 0..1 target openness (set by ping)
local current  = 0     -- smoothed openness, this tick
local previous = 0     -- smoothed openness, last tick (for render interpolation)

-- Sent by the host, runs on every client that can see you (including you).
function pings.setMouth(step)
   target = step / STEPS
end

local uuid = avatar:getUUID()
local manualKey = MANUAL_KEY and keybinds:newKeybind("Mouth (manual)", MANUAL_KEY) or nil

-- Host only: 0..1 from the microphone and/or the manual key.
local function readOpenness()
   local open = 0

   -- `plasmovoice` is nil when Plasmo Voice (or DTIMC's integration) is not loaded.
   if plasmovoice and not plasmovoice.isMuted(uuid) then
      local level = plasmovoice.getVoiceLevel(uuid)
      open = math.clamp((level - LEVEL_CLOSED) / (LEVEL_OPEN - LEVEL_CLOSED), 0, 1)
      if DEBUG then
         host:setActionbar(string.format("voice level %.2f  ->  mouth %.0f%%", level, open * 100))
      end
   end

   if manualKey and manualKey:isPressed() then
      open = math.max(open, 0.85)
   end

   return open
end

local lastStep, sendCooldown = -1, 0

function events.TICK()
   -- Host: sample the mic and tell everyone (max 10 pings/second, only on change).
   if host:isHost() then
      sendCooldown = sendCooldown - 1
      local step = math.floor(readOpenness() * STEPS + 0.5)
      if step ~= lastStep and sendCooldown <= 0 then
         lastStep = step
         sendCooldown = 2
         pings.setMouth(step)
      end
   end

   -- Everyone: ease toward the target (attack faster than release feels natural).
   previous = current
   local rate = (target > current) and ATTACK or RELEASE
   current = math.lerp(current, target, rate)
end

local function applyMouth(open)
   if MODE == "rotate" then
      MOUTH:setRot(open * MAX_ROT, 0, 0)
   elseif MODE == "scale" then
      MOUTH:setScale(1, MIN_SCALE + (1 - MIN_SCALE) * open, 1)
   elseif MODE == "uv" then
      local frame = math.floor(open * (UV_FRAMES - 1) + 0.5)
      MOUTH:setUVPixels(0, frame * UV_FRAME_H)
   end
end

function events.RENDER(delta)
   applyMouth(math.lerp(previous, current, delta))
end

-- ----------------------------------------------------------------------------
-- Optional: react to each voice packet you send (fired by the DTIMC mod).
-- Args: activationId (string), distance (number). Host only.
--
-- events["FIGEXTRA.PLASMOVOICE_PLAYER_SPEAK"]:register(function(activationId, distance)
--    -- e.g. play a particle, tweak the eyes, etc.
-- end)
-- ----------------------------------------------------------------------------
