(ns mzero.game.board-test
  (:require [clojure.test :refer [testing is are]]
            [mzero.utils.testing
             #?(:clj :refer :cljs :refer-macros) [check-all-specs deftest]]
            [mzero.game.board :as gb]))

(check-all-specs mzero.game.board)

(def small-test-board
  [[:empty :empty :wall :empty :empty]
   [:empty :fruit :empty :empty :empty]
   [:empty :empty :wall :empty :empty]
   [:empty :empty :empty :cheese :empty]
   [:empty :empty :fruit :cheese :empty]])

(deftest board-stats-test
  (testing "Board stats work"
    (let [{:keys [density total-cells non-wall-cells]} (gb/board-stats small-test-board)]
      (is (= 25 total-cells))
      (is (= 23 non-wall-cells))
      (is (= (-> 2 (* 100) (/ non-wall-cells) int) (density :fruit)))
      (is (= (-> 2 (* 100) (/ non-wall-cells) int) (density :cheese)))))
  (testing "Density only considers non-walls"
    (let [small-board [[:wall :fruit :fruit :wall :wall]
                       [:wall :wall :wall :wall :wall]
                       [:wall :wall :empty :empty :wall]
                       [:wall :wall :wall :wall :wall]
                       [:wall :wall :wall :wall :wall]]]
      (is (= 50 (-> small-board gb/board-stats :density :fruit)))
      (is (= 0 (-> small-board gb/board-stats :density :cheese))))))

(deftest get-closest-test
  (testing "Rets the closest int or nil"
    (are [coll i res] (= res (gb/get-closest coll i))
      [7 4 3 8] 2 3
      [5 10 10 12] 7 5
      [3 4 5 6] 3 3
      [3 5 6] 4 5
      [] 3 nil)))
       
      
(deftest find-in-board-test
  (testing "Finds the correct positions on a small test board, position [0 0]"
    (are [expected pred] (= expected (gb/find-in-board small-test-board pred))
      [0 0] #{:empty}
      [1 1] #{:fruit}
      [0 2] #{:wall}
      [0 2] #{:fruit :wall}))
  (testing "Finds the correct positions on a small test board, position [2 2]"
    (are [expected pred] (= expected (gb/find-in-board small-test-board pred [2 2]))
      [2 3] #{:empty}
      [1 1] #{:fruit}
      [2 2] #{:wall}
      [2 2] #{:fruit :wall})))
