(ns mzero.utils.commons
  "Utils for both Clojure and ClojureScript")

(defn reduce-until
  "Like clojure.core/reduce, but stops reduction when `pred` is
  true. `pred` takes one argument, the current reduction value, before
  applying another step of reduce. If `pred` does not become true,
  returns the result of reduce"
  ([pred f coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) coll))
  ([pred f val coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) val coll)))

(defn filter-keys
  "Like select-keys, with a predicate on the keys"
  [pred map_]
  (select-keys map_ (filter pred (keys map_))))


(defn filter-vals
  "Like filter-keys, except on vals"
  [pred map_]
  (select-keys map_ (filter #(pred (map_ %)) (keys map_))))

(defn currTimeMillis []
  #?(:clj (System/currentTimeMillis)
     :cljs (. (js/Date.) (getTime))))

(defn get-rng
  "Used to generate a seeded RNG to bind with g/*rnd*, and ignored for CLJS"
  [seed]
  #?(:clj (if seed
            (java.util.Random. seed) (java.util.Random.))
     :cljs (js/console.warn "Call to get-rng with seed not implemented
     in javscript. Continuing with unseeded random")))
