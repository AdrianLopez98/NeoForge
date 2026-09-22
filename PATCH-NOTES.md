# NeoForge — What's new

# Version 3.5

## Ascent: relics for every colour — 54 to 81

The rest of a batch of relic ideas sent in by a player. Blue, red and green now have their own,
like white and black already did: **9 blue, 8 red and 10 green**. As before, a coloured relic is
only offered if your deck plays that colour, so a mono-green run and a mono-blue run no longer
see the same things.

A few highlights: *Frostbound Heart* puts a stun counter on every creature an opponent plays;
*Kindling Blade* makes your damage to opponents one higher; *Relentless Totem* gives you a second
combat after your first; *Earthwaker Ring* turns a land into a creature as big as your land
count; and *Mirrorwake Shield* — the strongest of the lot — copies any nonland permanent,
yours or theirs, every upkeep.

Two ideas didn't go in because they were **exactly** an existing colourless relic ("+1/+0 to your
creatures" is *Sharpened Fang*, "draw two at the start" is *Oracle Lens*): a coloured copy of a
universal relic adds no variety, it just makes the same one come up twice.

## Adventure: your collection isn't judged by format ban lists

Reported on Reddit: loading a Realm of Legends save from Forge and editing a deck said many cards
were "not legal in Adventure" — and the warning was ours. Adventure has no format ban list; your
collection is what you own.

## Adventure: a visible concede button

Also from that report: *"I don't seem to find a concede button in the battle screen"*. It existed
behind Escape, but Escape is taught by the tutorial, and you reach Adventure through Forge's own
map without passing through it. There is now a button on screen.

## Attack with everything except tokens

A new shortcut next to "attack with everything". A true alpha strike risks your one-of creature
to a block that kills it, while tokens come back on their own — so this sends everything that
isn't a token.

## See a card's printed text

When you enlarge a card there is now **"Show card text"**: the printed text in plain form and in
your language. It answers a different question from "Show current text" (what the card has right
now, with everything gained and lost), so it's available everywhere, not only on the battlefield.

## Fixed: cards clipped at the edges when enlarged on hover

With the new mouse-over size setting at its higher values, a hovered card could be cut off with a
straight edge at the sides or top. The space around the hand and the rows is now sized to what the
card actually grows to.

## Engine update

The Forge rules engine is updated to 22 September 2026, with the latest card additions and fixes.

---

# Version 3.4

A small fix release. Everything here came from players reporting things in the days
after 3.3 — thank you.

## Ascent: one entry per commander in the picker

The commander picker was listing **every printing** of a card instead of the card, so the
same legendary showed up four or eight times in a row: 11,090 entries for 3,726 actual
commanders. It now shows one per name.

That also fixes something that looked like a separate bug: those duplicates all showed
**the same artwork**. When the exact printing isn't in your image cache, the art falls back
to a lookup by card name, so every printing drew the same illustration.

And a third one nobody had spotted: **"Let the game choose" was drawing from that same
unfiltered list**, so a commander with eight printings was eight times more likely to come
up than one with a single printing — and it could hand you an Arena-rebalanced card that
the picker itself hides. The button and the list now offer the same thing.

## Ascent: the "previous page" button works

The arrows worked out whether they should be clickable **once**, when the bar was built. You
start on page 1 with "previous" disabled, so it never turned back on; "next" started enabled,
which is why paging forward worked and paging back didn't. The same gap left the page counter
stale after a search.

## When a card can't be cast, we now tell you why

Clicking a card you can't cast used to say only **"Not there."**, which explains nothing — so
the reasonable conclusion was that the game was broken. It now gives the actual reason, using
the card's own words: *"There is no legal target right now: Select target creature spell you
control."*

That example is real. A player reported *Double Major* as a bug because it wouldn't let them
pick a creature — it copies a creature **spell on the stack**, not a creature on the
battlefield, and their stack was empty. The card was right; our message wasn't.

## Settings: how much a card grows on mouse over

**Settings → Enlarge the card on mouse over**: off, 108% (what it always was), 120%, 135% or
150%. It has a ceiling on purpose — beyond that it starts doing right-click's job, and then
two gestures mean the same thing.

## The Forge engine version is on the main menu

Bottom left: **Forge 2.0.15-SNAPSHOT · 2026-09-22**, and you can select and copy it. If you
hit something that's a rules or card bug rather than an interface one, that's the line
Card-Forge will ask you for. It updates itself whenever the engine is rebuilt.

---

# Version 3.3

## Ascent: relics that know what colours you play

The relic catalogue goes from 35 to **54**, and **19 of the new ones ask for a colour**. You
are only offered the relics your deck can actually use.

That restriction is the whole point rather than a limitation. A relic that has to work in
every deck ends up being "your creatures get +X/+X" in its thousand variations; one that knows
what colour you are can add {B}{B}{B} on your first turn, make Human tokens when you gain life,
give your creatures renown, bring a permanent back from your graveyard every turn, or make your
Swamps tap for an extra {B}. So two runs in different colours no longer offer you the same
things.

The other 35 have no colour requirement and still show up in every run, so a mono-white deck
sees the whole old catalogue **plus** the white ones — more relevant variety, not less.

Mana relics deliberately did **not** get more of the old treatment. "Add a mana at the start of
every main phase, forever" is an engine that snowballs a run; the new ones are a burst instead
— {B}{B}{B} once, on your first turn — or conditional, like the Swamps one.

## Ascent: pick the colours of your Standard deck

In Commander your colours are not a choice — your commander's identity decides them and the
rules reject anything else. In **Standard they are**, and until now the game rolled them for
you, which meant the biggest decision of a run (what you are going to play for the next forty
minutes) was made without asking.

The setup screen now has the five **WUBRG** letters and you can mark **up to two**. The line
underneath always tells you what will happen: *"Random — one or two colours will come out"*,
*"A single colour: white"*, *"Two colours: white and black"*. Mark nothing and it behaves
exactly as before.

Two is the limit on purpose: the seed deck is 30 cards with twelve lands, and at three colours
half your hands can't be cast.

## Ascent: three fixes, and one of them was invisible

- **Six relics were firing every turn instead of once.** *Lucky Coin*, a common, healed you 4
  **every upkeep** — more than its boss-tier equivalent — and *Wanderer's Compass*,
  *Oracle Lens* and *Hourglass of Kings* drew you a card every turn instead of once. Their text
  always said "at the beginning of your **first** upkeep"; now that is what they do.
- **The act 3 boss's second wind never worked.** At Ascension 10 the final boss is supposed to
  get a last-gasp buff when its life runs low. The same bug made it trigger on its *first*
  upkeep at full life and burn itself out, so nobody has ever seen the mechanic. Now they will.
- **Cards you can't cast are no longer offered as rewards.** *Lotus Bloom*, *Mox Tantalite* and
  four suspend-only sorceries could show up as a prize. They look like a jackpot and they are
  dead cards — the only way out of your hand is to suspend them and wait three to six turns.
- Fixed a seed deck that came out at **61 cards** on some runs, which happened with five-colour
  commanders that have very few basic lands.

## Oathbreaker: you can finally add the signature spell

Reported by a player: *"how do you add the signature spell?"*. You couldn't. The deck editor
only knew about one command-zone slot, so **every Oathbreaker deck was stuck** on *"is missing
a signature spell"*, and pasting a decklist from Moxfield silently dropped the spell.

The editor now knows about both slots: the footer has two buttons, and picking your planeswalker
leads straight into picking the spell. Commander is untouched.

## A straight hand, for 1080p screens

Reported by a player: *"angled cards look rough because of aliasing on 1080p"*. There is now a
setting — **Settings → Fanned hand** — that is on by default. Turn it off and your cards sit
straight, with no arc.

## The game log reads top to bottom

The log now runs in chronological order, opens showing the end, and has a **"Copy the log"**
button like the old Forge. Turns came out in order but the lines inside each turn were
reversed.

## "Playing Neo Forge" on Discord

Requested by a player. Your Discord status now shows what you're doing — *Commander · against
3 opponents / Turn 12* — with the logo beside it. On by default, and you can turn it off in
**Settings → Discord**.

It is decoration and it behaves like it: it never touches the rules engine, it can't stop you
playing, and with Discord closed it does nothing at all. It adds **no new dependencies** and
makes **no network connections** — it talks to the Discord app already running on your own
machine. Nothing about your opponents is sent: online games just say "private match".

---

# Version 3.2

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
- **And in Commander, the deck actually plays to your commander.** The seed deck is built
  from the cards people really run with that commander, and reward cards lean the same way —
  one in three at the start, two in three by the final boss, so early on you want *cards* and
  by the end you want *your* cards. Weak was always the point; going nowhere wasn't. The seed
  deck is still deliberately weak: this changes what it's about, not how strong it is.
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
