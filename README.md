# Mzero

A simple game of eating fruits in a maze, avoiding unpasteurized cheese and moving enemies. Implementations of various artificial algorithms to play this game (`players`).

## Setup

### Requirements ###
- Clojure
- Leiningen (who will take care of installing all other reqs)

For more info on requirements / dependencies and which version of what you need to install, see `project.clj`

### Installation
- Install by cloning this rep.

## The game

### Purpose
The game provides a simple programmatic world, and an interface to implement wannabe-intelligent players to interact in this world. The world's rules are those of the above described games. Therefore, a good player will be able to navigate the maze to eat lots of fruits, while avoiding enemies.

### Usage
Launching will run a single game using specified player & game-runner implementations, as well as various game options.

Start game:
- in CLI with `lein run args`;
- in REPL by getting to the `mzero.ai.clocked-threads-runner.ai.main` namespace and typing `(run args)`.

Run `lein run -h` to print CLI arguments. They are also described in `cli-options` at [mzero.ai.main](src/mzero/ai/main.clj).

#### Programmatic mode - interactive mode ###
In programmatic mode, game only displays initial and end states.

Game can be run interactively using the `--interactive/-i` flag. In that case:
- user can pause/step with [enter], run with r, quit wit q;
- game state is displayed every X (optionally specified) steps.

See main.clj for more details.

### Project structure
Game models and actions are in ``mzero.game``

3 main modules in `mzero.ai`:
- **main.clj** is responsible for launching the whole game;
- **world.clj** is responsible for running the world;
- **player.clj** is responsible for running the player.

Then, in directory **players**, a few basic player implementations.

### How world and player operate
The world is the *reality*, all that is external to the player's intelligence and senses--the player is defined by its senses and its movement decisions. In the real world, the player would be one's brain & nerves, sensing information and deciding actions. The world woud be everything else--including one's bodily functions.

Movement requests are the player's responsibility, while performing the actual movement is the world's responsibility. Theoretically, the player could request movements that are not executed, similarly to somebody suffering from the locked-in syndrome.

#### Target Architecture for AI Game

![Architecture image](https://docs.google.com/drawings/d/e/2PACX-1vT1ogu40fw8SG1oWGnR4WCJE3kmnCFcYzwMuLwiAuGbJ1vb8V2M8JzLFYiwczdS6D6cYqsMLmmyFO-_/pub?w=960&h=720)

# License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
