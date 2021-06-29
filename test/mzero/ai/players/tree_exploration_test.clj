(ns mzero.ai.players.tree-exploration-test
  (:require [mzero.ai.players.tree-exploration :as sut]
            [clojure.test :refer [is testing use-fixtures]]
            [mzero.utils.testing :refer [deftest count-calls]]
            [mzero.ai.world :as aiw]
            [mzero.game.state-test :as gst]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.ai.player :as aip]
            [mzero.ai.main :as aim]
            [mzero.game.board :as gb]
            [mzero.game.generation :as gg]
            [mzero.utils.utils :as u]))


(def world-state (-> gst/test-state-2
                     (assoc ::gs/player-position [2 0])
                     (assoc-in [::gb/game-board 4 2] :fruit)
                     aiw/get-initial-world-state))


(def ^:dynamic test-player)
(defn- multi-impl-fixture
  "Allows to run the same tests for various parameters of tree
  exploration. Here various implementations of nodes (via the node
  constructor)."
  [f]
  (doseq [constructor ["tree-exploration/te-node"
                       "java-dag/java-dag-node"]]
    (binding [test-player
              (aip/load-player "tree-exploration"
                               {:nb-sims 100
                                :node-constructor constructor
                                :seed 43}
                               world-state)]
      (f))))

(use-fixtures :each multi-impl-fixture)

(deftest update-children-test
  ;; test only valid for te-node
  (let [java-dag-node-player?
        (= (str(:node-constructor test-player)) "#'mzero.ai.players.java-dag/java-dag-node")]
    (or java-dag-node-player? 
        (let [test-node
              (reduce #(sut/append-child %1 %2)
                      ((-> test-player :node-constructor) (-> world-state ::gs/game-state))
                      '(:up :down :right))
              updated-node (#'sut/update-children test-node)]
          (is (contains? (sut/children updated-node) :left))
          (is (= (#'sut/update-children updated-node) updated-node))))))

(deftest tree-exploration-player-test  
  (let [nb-sims (:nb-sims test-player)
        tree-root (-> test-player
                      (aip/update-player world-state)
                      :root-node)]
    (is (every? #(<= (/ nb-sims 4) (::sut/frequency %) nb-sims)
                (-> tree-root ::sut/children vals)))
    (is (= ge/directions (keys (sut/children tree-root))))
    (is (every? #(>= (::sut/frequency %) 25) (vals (sut/children tree-root))))
    (is (= (int (-> tree-root (sut/get-descendant [:up]) sut/value)) 1))))

(deftest te-exploration-simulation-test
  (testing "Player should go eat the close fruit (up then right), then reset tree
    exploration from scratch--meaning after a simulation run the
    frequency of the root will be exactly the number of simlations"
    (let [{:keys [world player]}
          (aim/run (aim/parse-run-args "-t tree-exploration -n 2 -v WARNING")
            world-state test-player)
          nb-sims (:nb-sims player)
          root-node-after-sim
          (-> player (aip/update-player world) :root-node)]
      (is (= (-> world ::gs/game-state ::gs/player-position) [1 1]))
      (is (every? #(<= (/ nb-sims 4) (::sut/frequency %) nb-sims)
                (-> root-node-after-sim ::sut/children vals))))))

(deftest ^:integration te-blocking-bug
  :unstrumented
  (testing "After a while, the player should not stop moving. Frequent
  bug, reproduced on the below sample board starting at step 62 in
  which the player stops moving because it tries moving into a wall."
    (let [bugged-world-example
          (aiw/get-initial-world-state
           (first (gg/generate-game-states 1 20 2 true)))
          get-game-args
          #(aim/parse-run-args "-n %d -v WARNING" %)
          bugged-state
          (aim/run (get-game-args 62) bugged-world-example test-player)
          bugged-pos
          (-> bugged-state :world ::gs/game-state ::gs/player-position)
          next-player-positions
          (->> bugged-state
               (iterate #(apply aim/run (get-game-args 1) (vals %)))
               (take 10)
               (map #(-> % :world ::gs/game-state ::gs/player-position)))]
      (is (some (partial not= bugged-pos) next-player-positions)))))

(deftest ^:integration dag-blocking-bug
  :unstrumented
  (testing "Similar bug to above, happening with dag nodes"
    (let [bugged-world-example
          (aiw/get-initial-world-state
           (first (gg/generate-game-states 2 22 41 true)))
          get-game-args
          #(aim/parse-run-args "-n %d -v WARNING" %)
          bugged-state
          (aim/run (get-game-args 62) bugged-world-example test-player)
          bugged-pos
          (-> bugged-state :world ::gs/game-state ::gs/player-position)
          next-player-positions
          (->> bugged-state
               (iterate #(apply aim/run (get-game-args 1) (vals %)))
               (take 10)
               (map #(-> % :world ::gs/game-state ::gs/player-position)))]
      (is (some (partial not= bugged-pos) next-player-positions)))))

(deftest ^:integration te-stability-test
  :unstrumented ;; speed test would be hindered by instrumentation
  (testing "2 tests in 1 : stackoverflow bug and speed

    1/ ERROR: recursivity in tree-simulate should not throw
    stackoverflow even big boards

    2/ FAILURE: it should more than 75Kops/secs, on big boards.
    An op is ~ a call to tree-simulate. Also, at least 1000 sims per secs."
    (with-redefs [sut/tree-simulate (count-calls sut/tree-simulate)]
      (let [expected-sims-per-sec 1000
            board-size 50, nb-steps 10, sims-per-step 500
            time-to-run-ms
            (* nb-steps sims-per-step (/ 1000 expected-sims-per-sec))
            initial-world ;; seeded generator, always same board
            (aiw/get-initial-world-state
             (first (gg/generate-game-states 1 board-size 9)))
            game-run
            (future
              (u/timed
               (aim/run
                 (aim/parse-run-args "-n %d -v WARNING" nb-steps)
                 initial-world
                 (assoc test-player :nb-sims sims-per-step))))
            game-result
            (deref game-run time-to-run-ms nil)]
        
        (is (not (nil? game-result)) (str "time > than " time-to-run-ms))
        (let [nb-ops ((:call-count (meta sut/tree-simulate)))
              time-in-s (/ (first game-result) 1000)]
          (is (> (/ nb-ops time-in-s) 75000)
              (str "Nb of steps " nb-ops " in time " time-in-s)))))))

(deftest ^:integration proper-random-seeding
  :unstrumented
  (let [game-world
        (aiw/get-initial-world-state
         (gg/create-nice-game 15 {::gg/density-map {:fruit 5}} 42))
        steps-for-run-with-player
        #(-> (aim/run (aim/parse-run-args "-v WARNING") game-world %)
             :world
             ::aiw/game-step)]
    (testing "On the same board with same initial conditions, 2 games
  running with same seeding should have same number of steps."
      (let [test-player1 (assoc test-player :rng (java.util.Random. 44))
            test-player1-bis (assoc test-player :rng (java.util.Random. 44))
            test-player2 (assoc test-player :rng (java.util.Random. 45))
            test-player2-bis (assoc test-player :rng (java.util.Random. 45))]
          (is (= (steps-for-run-with-player test-player1)
                 (steps-for-run-with-player test-player1-bis)))
          (is (= (steps-for-run-with-player test-player2)
               (steps-for-run-with-player test-player2-bis)))))
    (testing "Games with different seeding should have different
  number of steps (with and without random-min flag as per
  multi-impl-fixture)"
      (let [test-player1 (assoc test-player :rng (java.util.Random. 44))
            test-player2 (assoc test-player :rng (java.util.Random. 45))]
        (is (not= (steps-for-run-with-player test-player1)
                 (steps-for-run-with-player test-player2)))))))
