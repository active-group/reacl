(ns reacl2.trace.console
  "Tracers to be used with [[reacl2.trace.core/add-tracer!]] that issue log entries via `js/console.log`."
  (:require [reacl2.trace.core :as trace]
            [reacl2.core :as reacl])
  (:refer-clojure :exclude [reduced cycle]))

;; small log dsl, for easier abstractions

(defrecord LogMulti [args])
(defn multi [& args] (LogMulti. args))
(defn multi? [v] (instance? LogMulti v))

(defrecord LogObj [value])
(defn obj [v] (LogObj. v))
(defn obj? [v] (instance? LogObj v))

(defrecord Styled [style args])
(defn styled [style & args] (Styled. style args))
(defn styled? [v] (instance? Styled v))

(defn interp [& args] (apply multi (interpose " " (remove #(and (multi? %) (empty? (:args %))) args))))

(defn- parse-log-args [args]
  (reduce (fn [[fmt-str args] a]
            (cond
              (multi? a) (let [[fmt2 args2] (parse-log-args (:args a))]
                           [(str fmt-str fmt2) (vec (concat args args2))])
              (obj? a) [(str fmt-str "%o") (conj args (:value a))]
              (integer? a) [(str fmt-str "%d") (conj args a)]
              (number? a) [(str fmt-str "%f") (conj args a)]
              (styled? a) (let [[fmt2 args2] (parse-log-args (:args a))
                                st (:style a)]
                            ;; Note: styling has to be 'turned on' and 'turned off' again. (cannot nest them)
                            [(str fmt-str "%c" fmt2 "%c") (vec (concat args [st] args2 [""]))])
              ;; strings, esp:
              :else [(str fmt-str "%s") (conj args a)]))
          ["" []]
          args))

(defn- log! [& args]
  (let [[fmt-str args] (parse-log-args args)]
    (apply js/console.log fmt-str args)))

(defn- log-table! [records & [columns]]
  (if (some? columns)
    (js/console.table (clj->js records) (clj->js columns))
    (js/console.table (clj->js records))))

(defn log-group-start! [& args]
  (let [[fmt args] (parse-log-args args)]
    (apply js/console.group fmt args)))

(defn log-group-end! []
  (js/console.groupEnd))

(declare show-comp)

(defn show-value [a]
  (cond
    (string? a) (obj a)
    (number? a) a
    :else (obj a)))

(defn- show-comp-arg [a]
  (cond
    (reacl/component? a)
    (show-comp a)
    ;; TODO: also nicer dom elements!?
    :else (show-value a)))

(def log-native-component? false)

(defn- comp-id [comp]
  (multi (styled "color: blue" (trace/component-class-name comp))
         (multi "@" (styled "color: grey" (trace/component-id comp)))))

(defn- comp-short-str [comp]
  (str (trace/component-class-name comp) "@" (trace/component-id comp)))

(defn- show-comp [comp]
  (if log-native-component?
    ;; with this, one can grab the comp from the log into a global variable in Chrome; but otherwise looks ugly and shows internals.
    (obj comp)
    (let [show-args (fn [args]
                      (multi "args: [" (apply interp (map show-comp-arg args)) "]"))
          show-app-state (fn [app-state]
                           (multi "app-state: " (show-value app-state)))]
      (multi "("
             (if (= reacl/uber-class (.-constructor comp))
               (interp (styled "font-style: italic" "toplevel")
                       ;; TODO: don't grab into reacl guts here:
                       (let [props (.-props comp)
                             class (aget props "reacl_toplevel_class")
                             app-state (aget props "reacl_uber_app_state")
                             args (aget props "reacl_toplevel_args")]
                         (multi (if (reacl/has-app-state? class)
                                  (show-app-state app-state)
                                  (multi))
                                (show-args args))))
               (interp (comp-id comp)
                       (if (reacl/has-app-state? (reacl/component-class comp))
                         (show-app-state (reacl/extract-app-state comp))
                         (multi))
                       (multi "local-state: " (show-value (reacl/extract-local-state comp)))
                       (show-args (reacl/extract-args comp))))
             ")"))
    #_(multi "(" (comp-id comp)
             (obj (cond-> {:local-state (reacl/extract-local-state comp)
                           :args (vec (reacl/extract-args comp))}
                    (reacl/has-app-state? (reacl/component-class comp)) (assoc :app-state (reacl/extract-app-state comp))))
             ")")))

(defn- show-comp-short [comp]
  (comp-id comp))

(defn- show-ret [ret]
  (let [ast (reacl/returned-app-state ret)
        lst (reacl/returned-local-state ret)
        msgs (reacl/returned-messages ret)
        acts (reacl/returned-actions ret)]
    (obj `(~'return ~@(when-not (reacl/keep-state? ast)
                        [:app-state ast])
                    ~@(when-not (reacl/keep-state? lst)
                        [:local-state lst])
                    ~@(mapcat #(list :message %) msgs)
                    ~@(mapcat #(list :action %) acts)))))

(defn- map-keys [f mp]
  (zipmap (map f (keys mp)) (vals mp)))

(defn- end-group [state]
  ;; ends an actual or marked group.
  (when (:group-open state)
    (log-group-end! #_(:cycle-id state)))
  (assoc state
         :group-open false
         :marked-group nil))

(defn- start-group [state & log-args]
  ;; Note: js/console allows nesting groups; this monad not.
  (let [state (if (:group-open state)
                (end-group state)
                state)]
    (apply log-group-start! log-args)
    (assoc state :group-open true)))

(defn- mark-group-start [state & log-args]
  ;; just keeps a group-start in 'memory', and only starts it one the first log following normal log-entry.
  (assoc state :marked-group (vec log-args)))

(defn- realize-marked-group! [state]
  (cond-> state
    (some? (:marked-group state)) (as-> $ (apply start-group $ (:marked-group state)))
    true (dissoc :marked-group)))

(defn log-in-group! [state & args]
  (let [state (realize-marked-group! state)]
    (apply log! args)
    state))

;; 5 main event types: rendering, sending msg, live-cycle returns, action reduced, and commit.

(def ^:private rendering-color "magenta")
(def ^:private message-color "green")
(def ^:private returned-color "blue")
(def ^:private action-color "red")
(def ^:private commit-color "brown")

(defn- marker [color & content]
  (apply styled (str "color: " color
                     "; padding: 1px 2px 1px 2px"
                     "; border-radius: 4px; border: 1px solid " color ";"
                     )
         content))

(def ^:private rendering (marker rendering-color "rendering"))
(defn- message-styled [& content] (apply styled (str "color: " message-color) content))
(def ^:private sending (marker message-color "sending"))

(defn- returned-styled [& content] (apply styled (str "color: " returned-color) content))
(def ^:private reduced (marker action-color "reduced action"))
(defn- action-styled [& content] (apply styled (str "color: " action-color) content))

(defn- commit-styled [& content] (apply styled (str "color:" commit-color) content))
(def ^:private commit (marker commit-color "commit"))

(defn- global [v]
  (if (reacl/keep-state? v)
    (styled "font-style: italic" "unchanged")
    (interp (commit-styled "set to") (show-value v))))

(defn- event [id]
  (multi "event #" id))

(defn- cycle [id]
  (multi "cycle #" id))

(defn- opt-label [label]
  (apply multi (when (some? label) (list label ": "))))

(defn component-tracer
  "A tracer that logs the parts of Reacl rendering cycles that involve
  all components matching the given predicate `(pred comp &
  args)`. The log entries are prefixed with the given `label`."
  [label pred & args]
  (trace/map-event-cycle-ids
   (trace/tracer
    {:group-open false}
    {trace/send-message-trace
     (fn [state event-id component msg]
       (when (pred component)
         (log! (opt-label label) (interp sending (show-value msg) (message-styled "to") (show-comp component))))
       state)

     trace/render-component-trace
     (fn [state event-id component]
       (cond-> state
         (pred component) (log-in-group! (opt-label label) (interp rendering (show-comp component)))))

     trace/returned-trace
     (fn [state event-id cycle-id component ret from]
       (cond-> state
         (pred component) (log-in-group! (interp (marker returned-color (str from)) (returned-styled "of") (show-comp component) (returned-styled "returned") (show-ret ret)))))
                      
     trace/reduced-action-trace
     (fn [state cycle-id component action returned]
       (cond-> state
         (pred component) (log-in-group! (interp reduced (obj action) (action-styled "from") (show-comp component) (action-styled "into") (show-ret returned)))))
                      
     trace/commit-trace
     (fn [state cycle-id global-app-state local-state-map]
       (let [local-state-map (filter (comp pred first) local-state-map)]
         (-> (if (or (not (reacl/keep-state? global-app-state))
                     (not (empty? local-state-map)))
               (let [state (realize-marked-group! state)]
                 (log-group-start! commit)
                 (try (log! (interp (commit-styled "toplevel app-state") (global global-app-state)))
                      (doseq [[component local-state] local-state-map]
                        (log! (interp (commit-styled "local-state of") (show-comp component) (commit-styled "set to") (show-value local-state))))
                      (finally
                        (log-group-end!)))
                 state)
               (do (log! (interp commit (styled "font-style: italic" "no changes")))
                   state))
             (end-group)
             ;; mark to start next cycle... first log-in-group that follows actually opens.
             (mark-group-start (multi (opt-label label) "cycle #" (inc cycle-id))))))})))

(defonce ^{:doc "A tracer that logs everything that causes and happens during a Reacl rendering cycle."} console-tracer
  (component-tracer nil (constantly true)))

(defn class-tracer
  "A tracer that logs the parts of Reacl rendering cycles that involve
  all components instantiated from the given Reacl class. The log
  entries are prefixed with the given `label`."
  ([clazz] (class-tracer nil clazz))
  ([label clazz]
   (component-tracer label (fn [component]
                             (= clazz (reacl/component-class component))))))
