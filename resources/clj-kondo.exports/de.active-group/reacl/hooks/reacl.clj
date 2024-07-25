(ns hooks.reacl
  (:require [clj-kondo.hooks-api :as api]))

(defn no-children? [node]
  (empty? (:children node)))

(defn sequential-node? [node]
  (not (no-children? node)))

(def isnt-binding-vector? no-children?)
(def is-binding-vector? sequential-node?)


(defn defclass
  [{:keys [node]}]
  (let [[class-name ?this ?app-state ?bindings & ?clauses] (rest (:children node))
        ;; NOTE if we want an app-state we need a this also!
        [maybe-this maybe-app-state bindings clauses]
        (cond
          (and (isnt-binding-vector? ?this)
               (isnt-binding-vector? ?app-state)
               (is-binding-vector? ?bindings))
          [?this ?app-state ?bindings ?clauses]

          (and (isnt-binding-vector? ?this)
               (is-binding-vector? ?app-state))
          [?this nil ?app-state (->> ?clauses
                                     (concat [?bindings])
                                     (filter some?))]

          (is-binding-vector? ?this)
          [nil nil ?this (->> ?clauses
                              (concat [?app-state ?bindings])
                              (filter some?))])

        clauses-map                 (into {} (mapv (fn [[k v]]
                                                     [(api/sexpr k) v])
                                                   (partition 2 clauses)))
        local-state                 (get clauses-map 'local-state)
        local                       (get clauses-map 'local)
        vector-let-like             (api/vector-node
                                     (concat (:children local-state)
                                             (:children local)
                                             (when-let [this maybe-this]
                                               [this (api/string-node "dummy-this-reference")])))
        clauses-without-let-like    (vals (dissoc clauses-map 'local-state 'local))
        bindings-with-state         (if-let [app-state maybe-app-state]
                                      (api/vector-node (cons app-state
                                                             (:children bindings)))
                                      bindings)
        opt-token                   (api/token-node 'opt)
        bindings-with-state-and-opt (api/vector-node (cons opt-token
                                                           (:children bindings-with-state)))
        body                        (if (empty? vector-let-like)
                                      clauses-without-let-like
                                      [(api/list-node
                                        (list* (api/token-node 'let)
                                               vector-let-like
                                               clauses-without-let-like))])
        new-node
        (if (some #(= '& (:value %)) (:children bindings))
          (api/list-node (list* (api/token-node 'defn)
                                class-name
                                bindings-with-state
                                body))
          (api/list-node (list (api/token-node 'defn)
                               class-name
                               (api/list-node
                                (list* bindings-with-state
                                       body))
                               (api/list-node
                                (list bindings-with-state-and-opt
                                      ;;NOTE the following list-node is only there, so that opt is used somehow
                                      (api/list-node
                                       (list (api/token-node 'println) opt-token))
                                      (api/list-node
                                       (list* class-name (:children bindings-with-state))))))))]
    {:node new-node}))
