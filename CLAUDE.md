# CLAUDE.md

## Communication
- Keep responses short and concise, but easy to understand. No long explanations.

## Git / GitHub
- **No Claude attribution anywhere.** Do not add `Co-Authored-By: Claude`, "Generated with Claude Code", session links, or any similar lines to commits, PRs, issues, or files.
- Only the user's name should appear in the repo's contribution history.
- Commit using the user's local git identity.

## Project
- Personal expense tracker, mobile-first, optimized for speed of entry.
- Home screen focus: numpad + enter button. Entering an amount adds it to today's expenses.
- Home screen also shows: current date, category picker, button to open the expenses list.
- Expenses list: entries can be edited/deleted.
- Categories (e.g. Food, Transpo, Hobbies) are user-customizable.
- Every CRUD operation shows a confirm popup, except adding an expense from the numpad/widget (keep that one tap).
- Design doc: `docs/superpowers/specs/2026-10-16-expenses-tracker-design.md`
