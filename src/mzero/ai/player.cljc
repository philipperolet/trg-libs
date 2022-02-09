(ns mzero.ai.player
  "Player protocol, and player-related functions:
  - `request-movement` to request a move,
  - `load-player` to load a player given its string name,
  with a seeding option."
  (:require [mzero.ai.world :as aiw]
            [mzero.utils.commons :as c]))

(defprotocol Player
  "A Player updates its state every time it needs via `update-player`.
  It can move by putting a non-nil direction for its move in its
  `:next-movement` field. `update-player` is called by
  `request-movement` and should probably not be called elsewhere.

  The 'world' will then execute the movement, which will reflect in
  the world state. It is possible that the player updates again
  *before* the world's execution of the movement, e.g. if the world
  has been paused by interactive mode. The player may have to take
  that into account, for instance by waiting until the movement is
  actually executed. When this happens, note that requesting the same
  movement rather than not requesting a movement might result in the
  movement being executed twice.

  Protocol implementations targeted at use by main.clj should be named
  {name}Player and reside in a namespace whose last fragment is
  {name}, e.g. the random player implementation will be `RandomPlayer`
  and will reside at `mzero.ai.players.random`, see `load-player`."
  
  (init-player [player opts world] "Implementation-specific
  initialization. Meant to be used by `load-player`, 
  and intended to be called with all fields of player
  record set to nil (any caller should expect fields to be ignored /
  overriden). Some opts may be implementation-specific and thus should
  be documented by implementations.

  The recommended way to create a player of any given
  impl is via `load-player`")

  (update-player [player world] "Updates player state, and ultimately
  the :next-movement field, every time a movement is requested."))

(defn request-movement
  "Update the player state, and indicate the player's move if any."
  [player-state world-state]
  (swap! player-state update-player @world-state)
  (when (-> @player-state :next-movement)
    (swap! world-state
           assoc-in [::aiw/requested-movements :player]
           (-> @player-state :next-movement))))

(defn- seed-player
  [player opts]
  (let [random-number-generator
          (if-let [seed (-> opts :seed)]
            (java.util.Random. seed)
            (java.util.Random.))]
    (assoc player :rng random-number-generator)))

(defn load-player
  "Load a player given its string type `player-type`.

  `opts` may contain `:seed` in which case the player will be
  initialized with a field `:rng` (random number generator)"
  [player-type opts world]
  (-> (c/load-impl player-type "mzero.ai.players" "Player")
      (seed-player opts) ;; seed before init (in case init needs seeding
      (init-player opts world)))
