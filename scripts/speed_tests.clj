(ns speed-tests
  "A few experiments to determine how fast various
  neanderthal/activation.clj ops are, sometimes compared to other
  alternatives."
  (:require [clojure.test :refer [deftest]]
            [mzero.ai.players.m0-modules.common :refer [ones]]
            [mzero.ai.players.m0-modules.neanderthal-impl :as sut]
            [mzero.utils.utils :as u]
            [mzero.utils.xp :as xp]
            [uncomplicate.neanderthal.core :as nc]
            [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.random :as rnd]
            [uncomplicate.neanderthal.vect-math :as nvm]))

(def default-dim 1024)
(def default-nb-iters 1000)

(defn- get-test-vectors
  [dim]
  {:i (rnd/rand-uniform! (nn/fv dim))
   :w (rnd/rand-uniform! (nn/fge dim dim))
   :w2 (rnd/rand-uniform! (nn/fge dim dim))
   :p (nn/fge dim dim)
   :wm (nn/fge dim dim)})

(defn- operations-to-time
  [dim]
  (let [outputs (nn/fv dim)
        {:keys [i w wm w2 p]} (get-test-vectors dim)]
    ;; cmpl = complexity = nb of floating-point operations per high-level op
    [{:fn-var #'nc/mm! :cmpl (* 2 (Math/pow dim 3)) :args [1.0 w w2 0.0 wm]}
     {:fn-var #'nc/copy! :args [w wm] :cmpl (* dim dim)}
     {:fn-var #'nvm/mul! :args [w2 w wm] :cmpl (* dim dim)}
     {:fn-var #'nvm/abs! :args [wm] :cmpl (* dim dim)}
     {:fn-var #'nc/rk! :args [-1.0 i (ones (nc/ncols w)) wm] :cmpl (* dim dim)}]))

(defn- op-for-dim
  [dim op-to-time]
  (let [outputs (nn/fv dim)
        {:keys [i w wm w2 p]} (get-test-vectors dim)

        ops-list
        ;; cmpl = complexity = nb of floating-point operations per high-level op
        [{:fn-var #'nvm/relu! :cmpl (* dim dim) :args [w]}
         {:fn-var #'nvm/mul! :cmpl (* dim dim) :args [w w2]}
         {:fn-var #'nc/sum :cmpl (* dim dim) :args [w]}
         {:fn-var #'nc/mm :cmpl (* 2 dim dim dim) :args [w w2]}
         {:fn-var #'nvm/abs! :cmpl (* dim dim) :args [w]}
         {:fn-var #'nvm/sqr! :cmpl (* dim dim) :args [w]}
         {:fn-var #'nvm/floor! :cmpl (* dim dim) :args [w]}
         {:fn-var #'nc/rk! :args [-1.0 (ones (nc/mrows wm)) (ones (nc/ncols w)) w] :cmpl (* dim dim)}
         {:fn-var #'nvm/fmax! :cmpl (* dim dim) :args [w2 w]}
         {:fn-var #'nvm/fmax! :cmpl (* dim dim) :args []}
         {:fn-var #'nc/scal! :cmpl (* dim dim) :args [1.001 w]}
         {:fn-var #'nn/fge :cmpl (* dim dim) :args [dim dim]}
         {:fn-var #'nc/mm! :cmpl (* dim dim) :args [w (nn/fgd dim (repeat dim 1.001))]}]]
    (first (filter #(= (:fn-var %) op-to-time) ops-list))))

(defn- time-to-gflops
  "Divide nb of ops by time in secs (not msecs) and 1G to get Gigaflops"
  [time-ms cmpl]
  (/ cmpl (/ time-ms 1000) 1000000000))

(defn- time-and-gigaflops-for-operation
  [{:keys [fn-var cmpl args]} nb-iters]
  (xp/timed-measures fn-var #_(u/with-logs fn-var (fn [& _] (str nb-iters "iterations")))
                     args
                     nb-iters
                     #(vector (first %) (time-to-gflops (first %) cmpl))))

(defn time-op
  ([op-to-time nb-iters dim]
   (let [[op-time op-gflops] (time-and-gigaflops-for-operation (op-for-dim dim op-to-time) nb-iters)
         fn-name (u/fn-name op-to-time)]
     (print (xp/stats-string (str "Ms per op - " fn-name)
                             op-time))
     (print (xp/stats-string (str "Gflops per op - " fn-name)
                             op-gflops))))
  ([op-to-time nb-iters]
   (time-op op-to-time nb-iters default-dim))
  ([op-to-time]
   (time-op op-to-time default-nb-iters)))

(deftest ^{:doc "Experiments on activation.clj operations. Approximate
  the GFlops performed per op."}
  speed-xp
  (let [display-gflops-per-op
        #(print (print (xp/stats-string (str "Gflops per op - " (u/fn-name (:fn %)))
                                        (map first (time-and-gigaflops-for-operation % default-nb-iters)))))]
    (doall (map display-gflops-per-op (operations-to-time default-dim)))))


(defn- msqi-pdm!
  "Clone of patter-dist-matr to experiment on timings"
  ([i p wm w]
   (let [substract-inputs-to-cols!
         #(nc/rk! -1 i (ones (nc/ncols p)) %)]
     (-> (nc/copy! p wm)
         #_substract-inputs-to-cols!
         #_nvm/abs!
         (nvm/mul! w)))))

(defn msqi
  "Mul! Speed Quirk investigation - why is mul faster in pdm than naturally?"
  []
  (let [{:keys [i w wm p w2]} (get-test-vectors 1024)]
    (time (some? (doall
                  (repeatedly 1000 #(msqi-pdm! i w2 wm w))))))
  (let [{:keys [i w wm p w2]} (get-test-vectors 1024)]
    (time (some? (doall (repeatedly 1000 #(nvm/mul! w w2))))))
  (let [{:keys [i w wm p w2]} (get-test-vectors 1024)]
    (time (some? (doall (repeatedly 1000 #(nvm/mul! w w2 wm)))))))
