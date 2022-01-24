(ns mzero.utils.cdg
  "Convenience namespace used by generation.cljc when in CLJS mode,
  instead of clojure.data.generators (CDG), because CDG is not available in ClojureScript.

  It is limited to fns used by generations.clj and does not exactly
  replicate cdg fn's contracts. In particular, no seedability.")

(def ^:dynamic *rnd*)

(defn uniform [lo hi]
  (+ lo (rand-int (- hi lo))))

(defn rand-nth [coll] (clojure.core/rand-nth coll))

(defn shuffle [coll] (clojure.core/shuffle coll))

(defn int []
  (* (rand-int Integer/MAX_VALUE) (if (< 0.5 (rand)) -1 1)))
