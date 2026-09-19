# NeoForge — What's new

## Ascent — a new roguelike mode

A full run in 45–60 minutes, played against the AI on a branching node map.

- **Your life carries over between fights**, so every combat costs you something — but never
  more than you can recover from. **No fight starts below half your maximum life**, and beating an
  act boss **heals you to full** before the next act. Resting at a campfire also heals you
  completely, so the choice there is a clean one: full life, or one card fewer in your deck.
  Paying life is part of Magic (fetchlands, Phyrexian mana, *Necropotence*), and it shouldn't cost
  you the rest of the run.
- **Your deck is built during the run.** You start with a deliberately weak seed deck and
  improve it node by node — winning a fight lets you add one of three cards, and shops let
  you buy cards or remove a bad one. In a 30-card deck, cutting a card you can never cast is
  often worth more than adding a good one.
- **37 relics** give you permanent passives (your creatures get +1/+1, you gain life each
  upkeep, and so on). Bosses offer three and let you pick one.
- **24 events**, shops, rest sites and three acts, each set on a different plane.
- **Ten Ascension levels** that make the run harder in a different way each time, plus
  milestone unlocks that open new events as you play.
- Lose, and the run is over — you get a summary showing the deck you ended up with, which is
  usually nothing like the one you started with.

Ascent has its own entry in the main menu. Only one run exists at a time, on purpose: what
makes a decision matter is not being able to take it back.

### Fixed: beating the act 1 boss left you stuck on a finished map

If you played an earlier build of Ascent, you may have beaten the act 1 boss and found the map
had nowhere left to go. That was a real bug, and a bad one: nothing moved the run on to the next
act, so **no run could be completed at all** and winning never unlocked an Ascension level.

This is fixed. **Runs that are already stuck fix themselves** — open Ascent and your run
continues into act 2 with the life, credits, relics and deck you had.

Thanks to the player who reported it.

## Commander: see every opponent's battlefield at once

In three- and four-player Commander you can now see **every opponent's battlefield side by
side** instead of one at a time with tabs.

Turn it on in **Settings → "Show every opponent battlefield at once"**. It is **off by
default**: splitting the width between three players makes the cards noticeably smaller, so
it is a matter of taste rather than a straight upgrade.

**Please enable it before you start a game.** It changes how the table is laid out, so set it
from the menu rather than in the middle of a match.

What you get for it: you can see what every opponent has without clicking through tabs, and
you can pick who you attack by dragging straight onto that player — no need to switch tabs
first.

Two things worth knowing:

- If your window isn't wide enough to fit all the player bars at full size, the game keeps
  using tabs and tells you why. A clipped bar would hide an opponent's life total, which is
  exactly the number you need when deciding combat.
- The cards get smaller. **Right-click** enlarges any card, and **Ctrl + mouse wheel** zooms
  the table itself (middle-click or Ctrl + drag to move around).

## Other improvements

- **Commander damage is now shown per commander**, not just the highest number. The 21-damage
  limit is per commander, not a total — three opponents hitting you for 10 each will not kill
  you — and now you can actually see that on the bar.
- When the game asks you to order simultaneous triggers, there is a **"Select all in this
  order"** button. Some board states produce dozens of identical triggers where the order
  doesn't matter, and clicking through them one by one was tedious.
- Fixed cards being **cut off at the bottom** on very crowded battlefields. Cards that don't
  fit now overlap instead of being sliced in half.
