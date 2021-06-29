(ns mzero.ai.main-test
  (:require [mzero.ai.world :as aiw]
            [mzero.ai.main :as aim]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]
            [mzero.game.state-test :as gst]
            [mzero.utils.testing :refer [deftest]]
            [clojure.test :refer [is testing]]))

(deftest parse-run-args-err-test
  (testing "should throw when an arg isn't valid"
    (is (thrown-with-msg? java.lang.IllegalArgumentException
                          #"There were error\(s\) in arg.*parsing.*"
                          (aim/parse-run-args "-l WARNING")))))
(defn basic-run
  "Runs a basic game (removing cheese & enemies), expects the
  game to finish well"
  [game-runner]
  (let [test-state (-> gst/test-state-2
                       (assoc-in [::gb/game-board 3 2] :fruit)
                       (assoc-in [::gb/game-board 3 3] :fruit)
                       (assoc ::gs/enemy-positions []))
        game-result (:world (aim/run
                             (aim/parse-run-args "-r %s -v WARNING" game-runner)
                             (aiw/get-initial-world-state test-state)))]      
    (is (= :won (-> game-result ::gs/game-state ::gs/status)))
    
    (is (< 5 (-> game-result ::aiw/game-step)))
    
    (is (= (reduce #(assoc-in %1 %2 :empty)
                   (test-state ::gb/game-board)
                   '([1 1] [3 2] [3 3]))
           (-> game-result ::gs/game-state ::gb/game-board)))
    (testing "If number of steps is given, stops after said number of steps"
      (is (= 50 (->> (aim/run
                       (aim/parse-run-args "-v WARNING -n 50 -r %s" game-runner))
                     :world
                     ::aiw/game-step))))))

(deftest run-test-basic
  (basic-run "MonoThreadRunner"))

(deftest run-test-interactive
  (testing "Interactive mode : after going through 2 loops then
  quitting, there should be 3*STEPS performed"
    (with-in-str "\n\nq\n"
      (is (= (-> (aim/run (aim/parse-run-args "-i -n 33 -v WARNING"))
                 :world
                 ::aiw/game-step)
             99)))))

(deftest run-test-interactive-quit
  (with-in-str "q\n"
    (let [game-result (aim/run (aim/parse-run-args "-i -n 15 -v WARNING"))]
      (is (= (-> game-result :world ::aiw/game-step) 15)))))


