# Contributing to junto

Thanks for your interest in junto.

## Code contributions are paused (for now)

junto's core is under active, daily development — we're shaping the
fundamentals of how rooms, sync, and transfer work, and those internals
change quickly. Until that core is stable, **we're not accepting code
contributions (pull requests).** A PR opened against a moving foundation
would likely be invalidated by the next core change before it could land,
which isn't fair to your time.

This is a deliberate, temporary pause while we get to a solid, dependable
base. Once the core settles, this document will be updated with real
contribution guidelines — and we'd love your help then.

## Issues are very welcome

Reporting problems is the most valuable thing you can do right now. If you
hit a bug, a confusing message, or something that just doesn't work the way
you'd expect:

- **[Open an issue on GitHub](https://github.com/swayam-mishra/junto/issues)** —
  please search first in case it's already filed; if it is, add a comment or
  a 👍 rather than opening a duplicate.

A good report includes:

- what you did (the exact `junto create` / `junto join` command),
- what you expected vs. what happened,
- your OS and `junto version`,
- if it's a connection or sync problem, the output of `junto doctor`,
- and, if you can reproduce it, a run with `-debug` (the log path is printed
  on startup; it records relay/ICE/sync events but **no** file names, room
  codes, or other secrets — safe to attach).

Feature ideas and feedback are welcome as issues too.

## Security

Please **don't** open a public issue for a security vulnerability. Email
the maintainer at swayammishra1504@gmail.com instead, and we'll coordinate
a fix.
