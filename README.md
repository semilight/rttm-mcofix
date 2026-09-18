# RTTM-mcofix

A fork of [Ringosham's Real Time Translation Mod](https://github.com/ringosham/TranslateMod) for Minecraft 1.12.2 Forge, patched for MinecraftOnline's chat syntax and with various improvements.

Version 1.1.2. Forked from RTTM 6.0.1.

## Fixes/Improvements

- Fix translation for users with kits (<*Player> would previously not translate)
- Fix translation for relay messages (`[TG]`, `[DSC]`,  `[IRC]`)
- Fix sign translations in multiplayer (Right/left click a sign to to translate)
- Skips MCO's own translations (`[TR]` / `[TR->lang]`) (thank you butter for suggesting this, and thank you slime for implementing the `[TR->lang]`)
- A never-translate list, so you can leave chosen languages alone and still translate the rest (thank you Ruty for suggesting this)
- Automatically fall back to running translations on my own `lserv` box when Google translate fails. Also available as an engine option, alongside Google and Baidu
- Assorted GUI fixes
- And more that I probably forgot!

## Credits

Original mod by **Ringosham**. MCO fixes by **_semilight**.

## License

GPLv3, same as the original mod. See [LICENSE](LICENSE).
