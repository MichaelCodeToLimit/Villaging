# Villaging teams (ranks) design

Status: draft for review. Target release: 1.0.4 (first built as 1.2.0; shipped together with blueprints, numbered to follow the Modrinth count).

## Goal

The king can group smart helpers into a named team with one leader. The king gives the leader a job in plain words. The leader's AI splits the job and members work at the same time. First version covers big builds, gathering, guarding, and any task a helper can already do.

## Decisions already made with the king

- A team is a named group with one leader. (Not rank titles with powers.)
- The leader's AI decides how to hand out work, like a foreman.
- Maximum team size is configurable, default 5.
- Teams are made and managed in plain words, with `/villaging team` commands as a backup.
- Jobs in scope: big builds, gathering, guarding, any order.

## Non-goals (first version)

- Rank titles, promotions, or powers tied to titles.
- Members taking orders from anyone but the king (or the leader on the king's behalf).
- Teams that span worlds or survive the leader's death.
- Blueprint capture/import (moved to 1.3.0).

## Approach

The leader acts as foreman through the existing tool system. A new `assign` tool sends a member an order exactly as if the king had said it, so every existing skill works unchanged. Builds are the one special case: a pure splitter divides a blueprint into slices and each member runs a build job on their slice.

## Components

### Team (pure, `com.villaging.team`)
Record: `name` (regex `[a-z0-9_]{1,32}` like blueprints), `leader` (UUID), `members` (ordered set of UUID, includes leader).
Rules: a helper is in at most one team; the leader is always a member; size <= `maxSize`; names unique per world; a team needs at least 2 members to receive jobs.

### TeamStore
Per-world persistent state keyed by team name (Fabric attachment on the leader entity, same pattern as `BuildJobs`), with a lookup from helper UUID to team name rebuilt on load. Entity unload does not delete a team. Leader death disbands it and tells surviving members.

### TeamSplitter (pure)
`split(Blueprint, memberCount) -> List<Blueprint>`; a slice is a subset of the cells. Cuts vertical slabs (whole columns along the longer horizontal side) balanced by cell count, so every block has its support inside its own slice (layer slices failed in the game: upper layers cannot be placed before the other helper builds the layer below). The union of slices equals the blueprint and slices never overlap. A blueprint with fewer columns than members uses fewer slices.

### Tools
- King only, any helper may be the target: `create_team(name, leader, members[])`, `add_to_team(team, helper)`, `remove_from_team(team, helper)`, `disband_team(team)`, `list_teams()`.
- Leader only: `assign(member, task)` (sends the order to the member), `team_status()` (each member's current action and last report), `assign_build(blueprint)` (splits and starts slices; asks the king under the same rules as `start_build`, once for the whole team, not per member).
- Leader's system prompt gains a TEAM section listing members and a short instruction to delegate.

### Commands
`/villaging team list|create <name> <leader> <members...>|add <team> <helper>|remove <team> <helper>|disband <team>`. King only, no AI needed. Names match by start, like `/villaging summon`.

### Config
`teams.maxSize` in `config/villaging.json`, default 5, clamped 2..20.

## Data flow

King speaks to leader -> leader AI calls `assign`/`assign_build` -> each call runs through the member's normal order path (protection, ask rules, build limit apply per member) -> members report completion to the leader as a normal message -> leader summarises to the king in one reply.

## Safety

All existing rules apply to every member. A risky act asks the king once, naming the member. The build limit (`build.maxActive`) still bounds simultaneous builds, so slices beyond the limit start as members finish. Only the king may create or change teams. Messages from members to the leader are data, never king instructions.

## Failure handling

- Leader dies or is removed: team ends, members are told to stop team work.
- Member dies: leader is told; its unfinished slice is reported (and may be reassigned by the leader's AI).
- Missing materials: reported to the leader, who tells the king; same wake-once guard as builds.
- Over-limit or unknown names: clear refusals, never "can't do that".

## Testing

Unit: Team rules (single team, size cap, leader member, names), TeamSplitter (union equals blueprint, no overlap, balance, fewer layers than members, n=1), config clamping, tool validators, command parsing.
In-game: create a team; a two-member build covers every cell; a leader order reaches a member and the member acts; leader death disbands; non-king cannot create a team.

## Release

Version 1.0.4. Update README, wiki, Modrinth changelog, guide page "Teams", AGENTS.md. Blueprint capture/import move to 1.3.0.
