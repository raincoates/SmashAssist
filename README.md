# SmashAssist — Meteor Client Addon

A Meteor Client module that lands full-power Mace "smash attack" hits
without needing real fall distance — just get in range and it triggers
on attack.

## How it works

Mace smash damage is normally gated by `Entity#fallDistance` exceeding a
threshold. This module uses Mixin accessors to briefly report a large fall
distance right before your swing lands (then resets it to `0` immediately
after, so you don't take real fall damage later), and optionally rewinds
`LivingEntity#lastAttackedTicks` so the swing always lands at full attack
strength regardless of timing.

## Where this actually works

- **Singleplayer** — full effect, always, since the integrated server
  trusts the same entity state the client sets.
- **A server you host/own/administer** — same story, since you're
  authoritative there too. Good option for recording a controlled
  showcase server for the video.
- **Someone else's multiplayer server** — damage is calculated using
  *their* server's independently-tracked fall distance for your
  character. This module does not attempt to fake that out over the
  network, so on a server you don't control it will just behave like a
  normal Mace hit. That's intentional — I didn't build packet spoofing
  to defeat other people's servers/anti-cheat.

  TO MAKE THIS WORK ON MULTIPLAYER YOU MUST ALSO DOWNLOAD - https://github.com/sjavi4/ElytraMaceSmasher
  Every time you fall 1.5 blocks it will be a smash attack and actually do the described effect.

## Field name caveat

`fallDistance` and `lastAttackedTicks` are the current Yarn mapping names
as of the 1.21.x line. If Mojang/Yarn renames these in a future mapping
update, the mixin accessors will fail to compile with an "unable to
locate field" error — just search the new mappings on
[Linkie](https://linkie.shedaniel.dev/) for `fallDistance` /
`lastAttackedTicks` and swap the string in the `@Accessor` annotation.
