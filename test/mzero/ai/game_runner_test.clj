(ns mzero.ai.game-runner-test
  (:require [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.main-test :refer [basic-run]]
            [mzero.ai.world :as aiw]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.ai.game-runner :as gr]
            [mzero.ai.main :as aim]
            [clojure.spec.alpha :as s]
            [mzero.game.generation :as gg]))

(deftest run-test-basic-monothreadrunner
  (basic-run "MonoThreadRunner"))

(deftest add-fog-test
  (let [{:keys [::gb/game-board ::gs/player-position]}
        (-> (aiw/world 10 10)
            ::gs/game-state
            (assoc ::gs/player-position [1 1])) 
        everything-visible
        (#'gr/add-fog-to-board game-board player-position 5)
        nothing-visible
        (#'gr/add-fog-to-board game-board player-position 0)
        little-visible
        (#'gr/add-fog-to-board game-board player-position 2)]
    (is (= 0 (gb/count-cells everything-visible :hidden)))
    (is (= 99 (gb/count-cells nothing-visible :hidden)))
    (is (= 75 (gb/count-cells little-visible :hidden)))
    (is (not-every? #{:hidden} (little-visible 0)))
    (is (not-every? #{:hidden} (little-visible 3)))
    (is (every? #{:hidden} (little-visible 4)))
    (is (not-every? #{:hidden} (little-visible 9)))))

(deftest fog-of-war-test
  (let [level
        {::gg/density-map {:fruit 5 :cheese 3}
         :rules [:fog-of-war]}
        world
        (aiw/multilevel-world 25 25 [level])]
    (is (->> (aim/run (aim/parse-run-args "-t dumbot -n 100 -v WARNING") world)
             :world
             (s/valid? ::aiw/world-state)))))

(deftest weird-rule-test
  (let [level
        {::gg/density-map {:fruit 5 :cheese 3}
         :rules [:momentum-rule]}
        world
        (aiw/multilevel-world 25 25 [level])]
    (is (->> (aim/run (aim/parse-run-args "-t dumbot -n 100 -v WARNING") world)
             :world
             (s/valid? ::aiw/world-state)))))
