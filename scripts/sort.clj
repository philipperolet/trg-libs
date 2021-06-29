(ns sort
  (:require [clojure.test :refer [with-test is deftest run-tests]]))

(defn sort-list-a [l]
    (or (when (every? true? (map #(<= %1 %2) l (rest l))) l)
        (recur (reduce (fn [[frst & rst] elt] (conj rst (max frst elt) (min frst elt))) (list (first l)) (rest l)))))

(defn sort-list2 [l]
  (reduce (fn [lst elt]
            (loop [before '() after lst]
              (if (or (empty? after) (<= elt (first after)))
                (concat before (conj after elt))
                (recur (concat before (list (first after))) (rest after)))))
          [] l))

(defn sort-list3 [l]
  (if (empty? l) l
      (let [min-elt (apply min l)] (conj (remove #{min-elt} (sort-list2 l)) min-elt))))

(defn sort-list [[x & r]]
  (or (when (not x) '())
      (concat (sort-list (filter #(< % x) r)) (list x) (sort-list (filter #(> % x) r)))))

(defn sort-vec [v] ;; with a vector, no lazy seq
  (let [idxs (filter #(< (v %) (v (dec %))) (range 1 (count v)))]
    (or (when (empty? idxs) v)
        (recur (reduce #(assoc %1 %2 (%1 (dec %2)) (dec %2) (%1 %2)) v idxs)))))

(deftest sort-list-test
  (is (empty? (sort-list '())))
  (is (= '(1) (sort-list '(1))))
  (is (= '(1 2 3 4) (sort-list '(4 2 3 1))))
  (is (= (range 100) (sort-list (shuffle (range 100))))))

(deftest sort-vector-test
  (is (empty? (sort-vec [])))
  (is (= '(1) (sort-vec [1])))
  (is (= '(1 2 3 4) (sort-vec [4 2 3 1])))
  (is (= (vec (range 100)) (sort-vec (vec (shuffle (range 100)))))))
