NEO FORGE FOR LINUX (beta)
==========================

Play Magic: The Gathering against AI: Commander, Standard, Brawl, draft,
sealed, Quest, Ascent and more. Free and offline. Built on the Forge rules
engine.

It brings its own Java: there's nothing to install.


HOW TO PLAY
-----------

1. Extract the archive wherever you like (your home folder is fine):

       tar -xzf NeoForge-linux-x64.tar.gz

2. Run it:

       ./NeoForge/bin/NeoForge

3. Optional: add it to your application menu with

       ./NeoForge/add-to-menu.sh

The first launch takes a little longer: it reads the 33,000+ cards.


YOUR DATA
---------

Decks, settings, saved games and downloaded card images live inside the
NeoForge/datos folder. The game is portable: move the folder and everything
goes with it.

To update to a new version, extract the new one and copy your old "datos"
folder into it.


STEAM DECK
----------

In Desktop Mode, extract it and run NeoForge/bin/NeoForge. To play it from
Gaming Mode, add NeoForge/bin/NeoForge to Steam with "Add a Non-Steam Game".
The game needs a mouse-like pointer: the trackpad works.


IF IT DOESN'T START
-------------------

Neo Forge uses JavaFX, which needs the usual desktop libraries (GTK 3). On a
normal desktop they're already there; on a minimal system install your
distribution's GTK 3 package (libgtk-3-0 on Debian/Ubuntu, gtk3 on
Fedora/Arch).

Run it from a terminal to see what it says, and tell me on Discord or on the
itch.io page:

    https://dokkodolabs.itch.io/neo-forge
    https://discord.gg/fF5Tn7Z2pv

This build is compiled and tested automatically on Linux machines, but it
hasn't been played much on real hardware yet. Feedback is very welcome!
