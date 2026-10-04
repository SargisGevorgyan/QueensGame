# Puzzle generator

`QueensGame/Models/PuzzleGenerator.swift` builds an N×N puzzle with contiguous
realms and a **verified unique** solution. It is deterministic for a given seed,
which is what makes Daily puzzles reproducible.

## Entry point

```swift
PuzzleGenerator.generate(size: Int, rules: RuleSet, seed: UInt64) -> Puzzle
```

All randomness comes from `SeededRNG`, a SplitMix64 generator conforming to
`RandomNumberGenerator`. A seed of `0` is replaced by a fixed non-zero constant.

## Pipeline

```
for up to 30 attempts:
    solution = buildSolution()                 # random valid Queen placement
    for up to 12 attempts:
        regions = growRegions(solution)        # one realm per Queen
        if repairToUnique(regions, solution):  # reshape until unique
            return Puzzle(...)
fallback: return the last board built
```

### 1. `buildSolution`: the hidden answer

Randomised backtracking places one Queen per row in a shuffled column order,
skipping used columns and any column within one of the previous row's Queen
(the "no touching" rule only needs checking against the row above). With
`diagonalTwo` it also rejects a column exactly two away from the Queen two rows
up. The result is `solution[row] = column`.

### 2. `growRegions`: the realms

Each Queen seeds its own realm (realm index = its row). Realms then grow by
random flood fill:

- Each realm keeps a list of empty neighbouring squares.
- Each step, the realms with candidates are sorted by size and one is picked at
  random from the smallest 60%, which keeps realm sizes fairly balanced.
- That realm claims a random empty neighbour.

Any square left unclaimed is attached to a neighbouring realm. Because a realm
only ever grows into adjacent squares, every realm is 4-connected.

### 3. `repairToUnique`: forcing a single solution

A randomly grown board usually has more than one solution. Rather than throw it
away, the generator repairs it, for up to 300 rounds:

1. `firstAlternate` searches for any complete placement other than the intended
   solution. If there is none, the puzzle is unique and we're done.
2. For the rows where the alternate differs, in random order, take the
   alternate's Queen square and try to move it into a neighbouring realm.
3. Accept the move only if the realm it left is still contiguous
   (`regionContiguous`). Otherwise revert and try the next neighbour or row.
4. If no square can be moved, give up on this region layout (`false`).

Moving a square that an alternate solution depends on into a different realm
breaks that alternate (it now has two Queens in one realm, or none in another),
while the intended solution is untouched because only non-solution squares move.
Repeating this removes every alternate.

The final check runs `firstAlternate` once more, so a returned puzzle is always
proven unique under its `RuleSet`.

### Colours

`colorMap` shuffles the 9 palette slots and takes the first N, so realm colours
change between puzzles.

## Solvers

Two backtracking solvers share the same row-by-row search with column, realm
and adjacency pruning:

- `firstAlternate(size:regions:rules:avoiding:)` (private) stops at the first
  solution that isn't the intended one.
- `countSolutions(size:regions:rules:cap:)` (internal, used by tests) counts
  solutions up to `cap`. Tests call it with `cap: 2` and expect `1`.

## Daily seeding

```swift
PuzzleGenerator.dailyKey(date:size:)   // "yyyy-MM-dd|<size>" in the local time zone
PuzzleGenerator.dailySeed(date:size:)  // 64-bit FNV-1a hash of that key
```

The key uses `en_US_POSIX` and the Gregorian calendar so it doesn't change with
the device's locale. The same key is stored by `StatsStore` to remember
completed Dailies.

## Royal Guard placement

`pickGuard(for:seed:)` derives a second RNG from the round seed and picks a
random square that is not in the solution, so the Guard never blocks the
answer. Speed decrees choose a Guard during play with the view model's
`liveGuard`, which also requires the square to be empty.

## Changing the generator

- Keep it deterministic: take all randomness from the `rng` passed in, never
  from the system generator. Otherwise Daily puzzles stop being reproducible.
- Any new rule must be added to `RuleSet` and honoured in all four places that
  check placements: `buildSolution`, `firstAlternate`, `countSolutions` and
  `BoardRules.analyse`. Otherwise the generator can promise uniqueness under
  rules the validator doesn't enforce, or the other way round.
- Run `testGeneratedPuzzlesAreUniqueAndWellFormed`,
  `testDailyPuzzleIsDeterministic` and `testAssassinsRangeSolutionAvoidsDiagonalTwo`.
- Changing the algorithm or the RNG changes every Daily puzzle. That's fine, but
  say so in the PR.
- The web version has its own copy of the generator (see
  [Web version](web-version.md#generator)); decide whether the change should be
  mirrored there.
