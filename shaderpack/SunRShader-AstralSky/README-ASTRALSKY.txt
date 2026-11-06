SunRShader + AstralSky for Minecraft 26.3 / Iris
================================================

This pack preserves the complete SunRShader visual pipeline and replaces its
procedural night stars with the 8,404-entry BSC5P bright-star catalogue. Star
positions and the Milky Way orientation are calculated from observation date,
time, latitude and east-positive longitude.

Recommended installation:

1. Install the accompanying astralsky-1.0.0.jar in the instance's mods folder.
2. Put this pack (directory or ZIP) in shaderpacks and select it in Iris.
3. Edit config/astralsky.json while Minecraft is closed, then restart.

Time modes:

- SYSTEM_DATE_GAME_CLOCK: computer calendar date + Minecraft world clock.
- SYSTEM_REALTIME: computer calendar date and wall-clock time.
- FIXED_DATETIME: configured date and local hour.

The Iris Shader Pack Settings > Sky > Astronomy screen also exposes fallback
settings. When the companion mod is installed, config/astralsky.json is the
authoritative location/date configuration and works with both directories and
ZIP shader packs.

Latitude is north-positive. Longitude is east-positive. UTC offset is the
local civil-time offset, for example +8 for China Standard Time.
