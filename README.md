# Mzero-game

The Mzero Game (also called Lapyrinthe) is a simple game involving a rabbit eating fruits in a maze, avoiding unpasteurized cheese, moving drinks and viruses, with 6 randomly-generated levels to clear.


The game can be played here : [game.machine-zero.com](https://game.machine-zero.com). Click on the big red question mark to start the game.

**Note**: GUI tested on chromium-based and firefox browsers, **not** mobile compliant.

### Gameplay
Move the player with arrow keys, or e - d - s - f keys. Game starts at level 1; if the player clears all 6 predefined levels, they can see the ending.

The game starts getting hard at level 5.

### Edges
The edges of the maze are connected together, they are not walls. Moving down from the last row will bring the player on top, and moving left from column 0 will bring the player to the rightmost column.

### Cheat codes
Cheat codes allow to start directly at a given level, or to slow down the enemies, by adding the query string `?cheatlev=X&tick=Y` to the link above. 

X is the level (here counting from 0 instead of one, so between 0 and 5). Y is an indicator of the time between enemy moves, 100 is the default (e.g. 200 will make enemies move twice slower, 50 will make them move twice as fast).

### Artificial players
See below on how to create artificial players and have them play the game. Multiple artificial players have been implemented as baseline:

- **random**, selecting a move randomly uniformly at each step;
- **exhaustive**, trying every possible path on the maze in a breadth-first-search fashion;
- **tree-exploration**, a rudimentary RL algorithm using monte-carlo tree search exploration to assess move values.

# About the game

This game was developed:

- to learn about Clojure & Clojurescript;
- to tell my friends in a fun way that we were expecting a child (born on 2020/11/11), thus all the references to strawberries, unpasteurized cheese and alcohol avoidance;
- as a simple (at first) environment for training AI agents.

### Relevance of the game to train AI agents
Current AI RL algorithms could probably learn optimal strategies for the game very quickly. However, the game can straightforwardly be made arbitrarily complex:

- by making sophisticated mazes where finding the fruits is difficult;
- by adding rules simple to humans but hard to machines, that require some form of transfer learnign, symbolic reasoning, or abstract thought--lever that can make fruits appear in certain conditions, ability to destroy wall by performing specific move sequences, etc.;

These are appropriate properties to research artificial agents learning abstractions, high level reasoning, complex planning & decision making, etc., especially to observe how they behave.

In other simple games that have a narrow environment (e.g. cartpole), or in games that are already complex (e.g. modern videogames), it is tough to create new interesting rules for training agents. 

### Naming
Machine Zero is the name of my adventure to explore artificial intelligence. Lapyrinthe is a mix between Labyrinthe (french for maze) and Lapin (french for rabbit).

# Usage

## Requirements
- Clojure 1.10.1 or above
- Leiningen 2.7.1 or above

Other requirements / dependencies will be installed by leiningen, see the [lein project file](project.clj)

## Use on GUI
The game can be played by human or artificial players via a ClojureScript GUI, see [mzero-game-gui](https://github.com/sittingbull/mzero-game-gui). 

Human players can use the GUI directly here : [game.machine-zero.com](https://game.machine-zero.com). Click on the big red question mark to start the game.

**Note**: GUI tested on chromium-based and firefox browsers, **not** mobile compliant.

## Use on terminal
The game can be played by artificial players via a CLI or a REPL. 

### With CLI

Sample run on board of edge size 27, with a 'tree-exploration' artificial player:
```
git clone https://github.com/sittingbull/mzero-game.git
cd mzero-game
lein run -- -t tree-exploration -s 27
```
### With REPL
```
git clone https://github.com/sittingbull/mzero-game.git
cd mzero-game
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
