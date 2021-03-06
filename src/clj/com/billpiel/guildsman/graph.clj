(ns com.billpiel.guildsman.graph
  (:require [com.billpiel.guildsman.common]
            [com.billpiel.guildsman.tensor :as tsr]
            [com.billpiel.guildsman.util :as util])
  (:import [com.billpiel.guildsman.common GraphRef Graph Op]))

(defn id->node [^Graph {:keys [handle-lock state]}] (:id->node @state))
(defn hash->id [^Graph {:keys [handle-lock state]}] (:hash->id @state))
(defn macro-hash->outputs [^Graph {:keys [handle-lock state]}] (:macro-hash->outputs @state))
(defn handle->id [^Graph {:keys [handle-lock state]}] (:handle->id @state))
(defn id->outputs [^Graph {:keys [handle-lock state]}] (:id->outputs @state))

(defn init-graph-state []
  {:id->node {}
   :hash->id {}
   :handle->id {}
   :macro-hash->outputs {}
   :id->outputs {}
   :collections {}})

;; don't overwrite!
(defn- add-to-id->node
  [m {:keys [id] :as op}]
  (merge {id op} m))

(defn- add-to-hash->id
  [m {:keys [id hash] :as op}]
  (merge {hash id} m))

(defn- add-to-handle->id
  [m {:keys [id handle] :as op}]
  (merge {handle id} m))

(defn add-to-id->outputs
  [m {:keys [id inputs]}]
  (->> (interleave inputs (range))
       (partition 2)
       (reduce (fn [agg [input-id input-idx]]
                 (let [{:keys [scoped-id output-idx]} (util/parse-tf-id input-id)]
                   (->> [{:id id :input-idx input-idx}]
                        (partial into)
                        (update-in agg
                                   [scoped-id  output-idx]))))
               m)))

(defn- add-to-collections
  [m ^Op op colls]
  (let [{:keys [id]} op]
    (reduce (fn [agg c]
              (update agg
                      c
                      #(conj (or % [])
                             id)))
            m
            colls)))

(defn- add-op-to-state*
  [graph-state ^Op op collections]
  (-> graph-state
      (update :id->node add-to-id->node op)
      (update :hash->id add-to-hash->id op)
      (update :handle->id add-to-handle->id op)
      (update :id->outputs add-to-id->outputs op)
      (update :collections add-to-collections op collections)))

(defn add-op-to-state!
  [^Graph {:keys [handle handle-lock state]} ^Op op & [collections]]
  (swap! state add-op-to-state* op collections))

(defn add-macro-to-state*
  [graph-state hsh outputs]
  (update graph-state
          :macro-hash->outputs
          assoc hsh outputs))

(defn add-macro-to-state!
  [^Graph {:keys [handle handle-lock state]} hsh outputs]
  (swap! state add-macro-to-state* hsh outputs))

(defn mk-graph-ref
  [^Graph g]
  (GraphRef. (:closed g) (:handle-lock g)))

(defn create
  ([] (Graph. (com.billpiel.guildsman.GraphNI/allocate)
              (atom (init-graph-state))
              (atom false)
              (Object.)
              (atom 0)))
  ([graph-handle] (Graph. graph-handle
                          (atom (init-graph-state))
                          (atom false)
                          (Object.)
                          (atom 0))))

(defn- spit-bytes
  "Is there a better way?"
  [f ba]
  (let [bais (java.io.ByteArrayInputStream. ba)]
    (with-open [out (clojure.java.io/output-stream f)]
      (clojure.java.io/copy bais out))))

(defn ->graph-def-byte-array [^Graph g]
  (com.billpiel.guildsman.GraphNI/toGraphDef (:handle g)))

(defn write-graph-def-to-file [filename ^Graph g]
  (spit-bytes filename (->graph-def-byte-array g)))

(defn add-output-by-handle! [^Graph g handle idx]
  (throw (Exception. "NOT IMPLEMENTED")))

(defn get-global-var-init-assign-ops
  [^Graph g]
  (let [i->n (id->node g)]
    (->> g
         :state
         deref
         :collections
         :global-var-inits
         (map i->n))))
