---
version: "0.1.2"
level: copilot
processes:
  design: assist
  implementation: copilot
  testing: pair
  documentation: copilot
  deployment: pair
---

This format is based on [AI-DECLARATION.md](https://ai-declaration.md/en/0.1.2/).

## Notes

- **Design (assist).** What Neo Forge is, what it should feel like and what gets built is decided by the
  author, a software engineer: the features, which player requests are done and which aren't, and the
  architecture rule the whole project rests on (never modify a single Forge file, only add our own module,
  so every Forge update with its new cards comes in for free). The AI proposes options for parts of it, and
  the author chooses.
- **Implementation (copilot).** Most of the code was written with an AI assistant (Claude, by Anthropic),
  directed change by change by the author. Nothing goes in without the author asking for it and approving it.
- **Testing (pair).** The AI writes and runs the automated checkers (headless test games, screenshots,
  the Android emulator); the author plays every change, finds the bugs and confirms each fix before it
  ships. Many fixes come straight from players' reports on itch.io, Reddit and Discord.
- **Documentation (copilot).** Release notes and internal docs are drafted with the AI and reviewed by the
  author.
- **Deployment (pair).** Builds, GitHub releases and the Mac/Linux workflows are prepared with the AI; the
  author publishes on itch.io and decides what ships and when.
- The rules engine, the AI opponents and the 33,000+ card scripts are [Forge](https://github.com/Card-Forge/forge),
  written by its own contributors. This declaration covers only Neo Forge's own code.
