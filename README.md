# The Rabbit Game (libs)

### [The Rabbit Game](https://game.machine-zero.com) - A game cute for humans and tough for AIs

Discover various kinds of artificial intelligence algorithms by watching them play a simple game: a rabbit eating strawberries in a maze (humans can play too 😉). Play it [here](https://game.machine-zero.com).

**Note**: The game was tested on Chrome (& chromium-based) and firefox browsers; it is not yet mobile compliant unfortunately.

This repository contains core libs for the game engine, along with a few artificial players and a CLI. All the frontend/backend for the game website can be found [here](https://github.com/philipperolet/the-rabbit-game). Deep-learning based players (work in progress) are [here](https://github.com/philipperolet/trg-players).

## Purpose
The goal of The Rabbit Game is to demystify artificial intelligence
   a little, by watching how machines play the game. But first, you
   can play it a little yourself to see how it works, get a good score
   and try all the various levels.

Then, you can let artificial intelligences play. Check out the
    8 different artificial players; each has its own style determined
    by its stats--its strong and weak points (click on a stat to see
    what it means).

Have different AIs play on different levels. Each new level
   introduces something new that will make it tougher for AIs. See
   which ones get far, which one fail, and why they behave like
   this (click on \"Learn more about me\" to understand an AI's
   behaviour).

Additionnally, if you're a hacker, you can try to code an
    algorithm to go to the highest possible level. If it clears the
    last level, which is quite hard, you can win *a lot* of internet
    points (really awful lot).

Learn more about the story behind the game and why it's interesting for AI research [here](https://www.machine-zero.com/trg.html)
# Dev setup & usage 
## Requirements
- Clojure 1.10.1 or above
- Leiningen 2.7.1 or above

Other requirements / dependencies will be installed by leiningen, see the [lein project file](project.clj)

## Run with CLI or REPL
The game can be played by artificial players via a CLI or a REPL. 

### With CLI

Sample run on board of edge size 27, with a 'tree-exploration' artificial player:
```
git clone https://github.com/philipperolet/trg-libs.git
cd trg-libs
lein run -- -t tree-exploration -s 27
```
### With REPL
```
git clone https://github.com/philipperolet/trg-libs.git
cd trg-libs
lein repl
```

Then in the repl
```
(gon args-string) ;; run a game with a string of standard command-line args
(gon n) ;; run n steps of the current game (unless already finished)
(gon) ;; run 1 step of the current game
```

## Command-line arguments
Detailed list of possible run args :
```
lein run -- -h
```
(also described in `cli-options` at [mzero.ai.main](src/mzero/ai/main.clj))

The most important args are:

- **-s board_size**, the edge size of the square board on which the 
- **-S seed**, the random seed used to generate the maze, defaults to 0; change it to get a different board;
- **-t player_type** , string name of an artificial player implementation. Readily-available player implementations are 'random', 'exhaustive' and 'tree-search';
- **-L level**, map with level data : density of fruit, cheese, walls, nb of enemies, see spec in [mzero.game.generation](src/mzero/game/generation.cljc)

Note : the seed is specific to the maze generation. Players requiring randomness and/or a seed should use the **player-opts** arg.

## Programmatic mode - interactive mode
In programmatic mode, only the initial and end states are displayed.

Game can be run interactively using the `--interactive/-i` flag. In that case:
- user can pause/step with [enter], run with r, quit wit q;
- game state is displayed every X (optionally specified) steps.

See main.clj for more details.

## Creating and running artificial players
Information to create a player implementation is [here](src/mzero/ai/player.clj)

Multiple artificial players have been implemented as baseline, such as (non-exhaustive):

- **random**, selecting a move randomly uniformly at each step;
- **exhaustive**, trying every possible path on the maze in a breadth-first-search fashion;
- **tree-exploration**, a rudimentary RL algorithm using monte-carlo tree search exploration to assess move values.

# Code overview

Game models and functions to manipulate them are in ``mzero.game``:
- **board.cljc**: game board specification and utilities (game board = maze);
- **events.cljc**: game events (such as enemy or player movement) specifications and utilities;
- **state.cljc**: a state is a game board + being's positions + status (active/over/won, etc.) etc.;
- **generation.cljc**: utilities to generate nice boards and games (pure random genration would be uninteresting to play).

Game running logic is in `mzero.ai`:
- **main.clj** : CLI and game entry point, run function;
- **world.clj** environment update logic at each game step;
- **player.clj** player update logic at each game step;
- **game_runner.clj**: interface, with a few impls, to orchestration of a game run.

Artificial player implementations discussed above ('random', 'tree-exploration' and 'exhaustive') are in ``mzero.ai.players``.

#### Note : movement logic for world / player interaction
Movement requests are the player's responsibility, while performing the actual movement is the world's responsibility. Theoretically, the player could request movements that are not executed by the environment.

# License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
