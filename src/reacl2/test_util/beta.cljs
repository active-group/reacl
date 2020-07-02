(ns reacl2.test-util.beta
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom]
            ["react-test-renderer"]))

;; TODO: xpath/select adaptor in the test utility object (when mounted)?

;; TODO: utils that  are handy.  util/click-element from Simon.

(defrecord ^:private TestEnv [class has-app-state? renderer ret-atom])

(defn env?
  "Returns true if the given value is a test environment."
  [v]
  (instance? TestEnv v))

(reacl/defclass ^:private runner-class this [env class args ret-atom]
  render (if (:has-app-state? env)
           ;; TODO: (Merge app-state (first arg) into opts with next version)
           (apply class
                  (reacl/opt :reaction (reacl/pass-through-reaction this))
                  args)
           (apply class args))

  handle-message
  (fn [app-state]
    (swap! ret-atom reacl/merge-returned (reacl/return :app-state app-state))
    (reacl/return)))

(defn- instantiate [env & args]
  (assert (instance? TestEnv env))
  (let [class (:class env)
        ret-atom (:ret-atom env)
        red-act (fn [_ action] ;; TODO: static fn!?
                  (swap! ret-atom reacl/merge-returned (reacl/return :action action))
                  (reacl/return))]
    (reacl/instantiate-toplevel runner-class
                                (reacl/opt :reduce-action red-act)
                                env
                                class
                                args
                                ret-atom)))

(defn- find-component [env]
  ;; may return nil if class not mounted.
  (some-> (try (.-root (:renderer env))
               ;; react throws if nothing is rendered; and I don't want to add an extra flag to know it.
               (catch :default e
                 nil))
          (.findByType (reacl/react-class runner-class))
          (.-children)
          (aget 0)))

(defn- find-env [v]
  (if (instance? TestEnv v)
    v
    (loop [runner v]
      (if (not= (.-type runner) (reacl/react-class runner-class))
        (if-let [p (.-parent runner)]
          (recur p)
          (throw (ex-info "The given component or test environment is not mounted or not mounted anymore." {:value v})))
        ;; first arg of the runner is the test env:
        (let [[env] (reacl/extract-args (.-instance runner))]
          (assert (instance? TestEnv env))
          env)))))

(defn- get-env [v]
  (if (instance? TestEnv v)
    v
    (let [runner (.-parent v)]
      (if (not= (.-type runner) (reacl/react-class runner-class))
        (throw (ex-info "The given component or test environment is not mounted or not mounted anymore." {:value v}))
        ;; first arg of the runner is the test env:
        (let [[env] (reacl/extract-args (.-instance runner))]
          (assert (instance? TestEnv env))
          env)))))

(defn- test* [class has-app-state? options]
  (TestEnv. class has-app-state?
            (js/ReactTestRenderer.create nil (clj->js (into {} (map (fn [[k v]]
                                                          [(if (= k :create-node-mock)
                                                             :createNodeMock
                                                             k) v])
                                                        options))))
            (atom (reacl/return))))

(defn fn-env
  "Returns a test environment for tests of the given function, which
  must return a dom element or class instance. For `options` see [[env]]."
  [f & [options]]
  (assert (or (not (reacl/reacl-class? f))
              (not (reacl/has-app-state? f))))
  (test* f false options))

(defn env
  "Returns a fresh test environment for testing the given class. An
  `options` map may include a `:create-node-mock` function that is
  called with a React test renderer instance and may return a value
  that is then used for [[reacl2.core/get-dom]] on references."
  [class & [options]]
  (if (and (reacl/reacl-class? class)
           (not (reacl/has-app-state? class)))
    (fn-env class)
    (test* class true options)))

(defn- with-collect-return! [env f]
  (assert (instance? TestEnv env))
  (let [ret-atom (:ret-atom env)]
    (reset! ret-atom (reacl/return))
    (f)
    @ret-atom))

(defn- render [env elem]
  (assert (instance? TestEnv env))
  (.update (:renderer env) elem))

(defn- render-return [env elem]
  (with-collect-return! env
    (fn []
      (render env elem))))

(def ^{:arglists '([env app-state & args]
                   [env & args])
       :doc "Does a fresh mount of the class from the given testing
  environment with the given app-state and args, returning app-state
  changes and actions returned by the class in form of a
  `reacl/return` value."}  mount!
  (fn [env & args]
    (render env nil) ;; clean up to make it a mount even if called twice.
    (render-return env (apply instantiate env args))))

(defn is-mounted?
  "Returns if the given testing environment contains a mounted component (true between [[mount!]] and [[unmount!]])."
  [env]
  (some? (find-component env)))

(defn get-component
  "Return the component currently mounted in the given test
  environment. Throws if it is not mounted. Note that the returned
  component becomes invalid after the next call to [[mount!]]
  or [[unmount!]], but stays valid after an [[update!]]."
  [env]
  (or (find-component env)
      (throw (js/Error. "Nothing mounted into the test environment. Call mount! first."))))

(defn- get-component* [v]
  ;; like get-component, but just return v if it is a component already.
  (if (instance? TestEnv v)
    (get-component v)
    ;; pretend it is already a React.TestInstance   (no predicate for that?)
    (do ;; (assert (.hasOwnProperty v "instance") v)  (does not always work)
        v)))

(defn- get-component-instance [v]
  (.-instance (get-component* v)))

(defn ^:no-doc mount*
  "Mounts an instance of the given class into a fresh test environment, and returns what was returned on mount and the component."
  [class & args]
  (let [env (if (reacl/reacl-class? class)
              (env class)
              (fn-env class))
        ret (apply mount! env args)]
    [ret (get-component* env)]))

(def ^{:doc "Mounts an instance of the given class into a fresh test environment, and returns the component."
       :arglists '([class app-state & args]
                   [class & args])}
  mount
  (fn [class & args]
    (let [[ret comp] (apply mount* class args)]
      (when (not= ret (reacl/return))
        (js/console.warn "Mounting the tested class %s returned (%o). Use mount-ignore, or env and mount! instead." (reacl/class-name class) ret))
      comp)))

(def ^{:doc "Like [[mount]], mounts an instance of the given class into a fresh
  test environment, and returns the component, but does not issue a
  warning when the class returned something on mount."
       :arglists '([class app-state & args]
                   [class & args])}
  mount-ignore
  (fn [class & args]
    (second (apply mount* class args))))

(def ^{:arglists '([comp f & args]
                   [env f & args])
       :doc "Calls `(f comp & args)` if called with a component, or if
  called with a test environment, with the currently mounted toplevel
  class instance. In any case, after `f` has been evaluated for its
  side-effects, this returns what has been returned from the tested
  toplevel class in the form of a `reacl/return` value.

  Note that will usually not need to call this function directly; use
  the various utilities from this module to interact with a
  component."}  with-component-return
  (fn [c f & args]
    (let [env (find-env c)]
      (with-collect-return! env
        (fn []
          (apply f (get-component* c)
                 args))))))

(defn- with-component-instance-return
  [c f & args]
  (with-component-return c
    (fn [comp]
      (apply f (.-instance comp) args))))

(defn- get-reacl-instance [v]
  (let [inst (get-component-instance v)]
    (assert (reacl/reacl-class? (reacl/component-class inst))) ;; TODO: throw
    inst))

(defn- with-reacl-instance-return
  [c f & args]
  (with-component-instance-return c
    (fn [inst]
      (assert (reacl/reacl-class? (reacl/component-class inst)))
      (apply f inst args))))

(def ^{:arglists '([comp] [env])
       :doc "Return the current local-state of a component, or if
  given a test environment, of the toplevel component. Throws if it is
  not mounted."}
  inspect-local-state
  (fn [c]
    (reacl/extract-local-state (get-reacl-instance c))))

(def ^{:arglists '([env msg] [comp msg])
       :doc "Sends the given message to the given component, or the component
  currently mounted in the given test environment. Returns a changed
  app-state and actions from the toplevel class, in the form of a
  `reacl/return` value. Throws if it is not mounted."}
  send-message!
  (fn [c msg]
    (with-reacl-instance-return c
      (fn [inst]
        (js/ReactTestRenderer.unstable_batchedUpdates
         #(reacl/send-message! inst msg))))))

(defn invoke-callback!
  "Invokes the function assiciated with the given `callback` of the
  given dom element (e.g. `:onclick`) with the given event object, and
  returns a changed app-state and actions from the toplevel class, in
  the form of a `reacl/return` value. Typically, you will first search for
  the element via [[xpath/select-one]].

  Note that this does not do a 'real' DOM event dispatching, e.g. no
  bubbling or canceling phase, and no default effects."
  [elem callback event]
  (with-collect-return! (find-env elem)
    (fn []
      (js/ReactTestRenderer.unstable_batchedUpdates
       #(let [n (aget dom/reacl->react-attribute-names (name callback))]
          ((aget (.-props elem) n) event))))))

;; click-element? change-element? some very common event objects?

(def ^{:arglists '([comp app-state & args]
                   [comp & args]
                   [env app-state & args]
                   [env & args])
       :doc "Performs an update of the given toplevel component, or the toplevel
  component currently mounted in the given test environment. Returns a
  changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."}
  update!
  (fn [c & args]
    (let [env (get-env c)]
      (render-return env (apply instantiate env args)))))

(defn unmount!
  "Performs an unmount of the component mounted in the given test
  environment. Returns a changed app-state and actions from the class,
  in the form of a `reacl/return` value."
  [env]
  (when-not (is-mounted? env)
    (throw (js/Error. "Something must be mounted to be unmounted. Call mount! first.")))
  (render-return env nil))

(def ^:dynamic *max-update-loops* 100)

(defn push!
  "If the given 'return' value contains an app-state change, then
  update the given test environment, merging in another 'return' value
  resulting from the update - 'pushing' the update cycle forward."
  [env ret]
  (let [st (reacl/returned-app-state ret)]
    (if (not (reacl/keep-state? st))
      (reacl/merge-returned ret (update! env st))
      ret)))

(defn push!!
  "If the given 'return' value contains an app-state change, then
  update the given test environment, merging in another 'return' value
  resulting from the update, and repeat until the app-state does not
  change anymore. Stops with an exception when [[*max-update-loops*]]
  are exceeded."
  [env ret]
  (loop [r ret
         state reacl/keep-state
         n 1]
    (when (> n *max-update-loops*)
      (throw (ex-info "Component keeps on updating. Check the livecylcle methods, which should eventually reach a fixed state." {:intermediate-state state})))
    (let [st (reacl/returned-app-state r)]
      (if (not= state st)
        (recur (push! env r) st (inc n))
        r))))

(defn inject-return!
  "This injects or simulates a [[reacl/return]] from a method of the
  class the given component was instantied from. Returns a
  changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."
  [comp ret]
  (assert (reacl/returned? ret))
  ;; Note: there is a way to find 'real' components starting from the root of the test instance hierarchy... but do not do this for now.
  (assert (some? (.-instance comp)) "The given component is not extracted from the test environment.")
  (let [instance (.-instance comp)
        class (reacl/component-class instance)]
    (when-not (reacl/reacl-class? class)
      (throw (ex-info "The given component is not an instance of a reacl class." {:value comp})))
    (assert (or (reacl/keep-state? (reacl/returned-app-state ret))
                (reacl/has-app-state? class)))
    (with-collect-return! (find-env comp)
      (fn []
        (js/ReactTestRenderer.unstable_batchedUpdates
         #(reacl/toplevel-handle-returned! instance ret 'injected))))))

(defn inject-change!
  "This injects or simulates a `(return :app-state state)` from a
  method of the class the given component was instantied from. Returns
  a changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."
  [comp state]
  (inject-return! comp (reacl/return :app-state state)))

(defn inject-action!
  "This injects or simulates a `(return :action action)` from a
  method of the class the given component was instantied from. Returns
  a changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."
  [comp action]
  (inject-return! comp (reacl/return :action action)))

(def ^{:arglists '([comp state] [env state])
       :doc "Sets the local-state of a component, or the toplevel component
  currently mounted in the given test environment. Returns a changed
  app-state and actions returned from the toplevel class as a result,
  in the form of a `reacl/return` value. Throws if it is not mounted.

  Note that it is a bit dangerous to base tests on this, and should
  only be used with care, if the way to get to this state is otherwise
  impossible in a unit test."}

  inject-local-state!
  (fn [c state]
    ;; these should be equivalent:
    #_(reacl/set-local-state! (get-reacl-instance tc) state)
    (inject-return! (get-component* c) (reacl/return :local-state state))))

(defn mount!!
  "Like [[mount!]], but followed by a [[push!!]]."
  [env & args]
  (push!! env (apply mount! env args)))

(defn update!!
  "Like [[update!!]], but followed by a [[push!!]]."
  [c & args]
  (push!! (get-env c) (apply update! c args)))

(defn unmount!!
  "Like [[unmount!]], but followed by a [[push!!]]."
  [env]
  (push!! env (apply unmount! env)))

(defn invoke-callback!!
  "Like [[invoke-callback!]], but followed by a [[push!!]]."
  [elem callback event]
  (push!! (find-env elem) (invoke-callback! elem callback event)))

(defn send-message!!
  "Like [[send-message!]], but followed by a [[push!!]]."
  [c msg]
  (push!! (find-env c) (send-message! c msg)))

(defn inject-return!!
  "Like [[inject-return!]], but followed by a [[push!!]]."
  [comp ret]
  (push!! (find-env comp) (inject-return! comp ret)))

(defn inject-action!!
  "Like [[inject-action!]], but followed by a [[push!!]]."
  [comp action]
  (inject-return!! comp (reacl/return :action action)))

(defn inject-change!!
  "Like [[inject-change!]], but followed by a [[push!!]]."
  [comp state]
  (inject-return!! comp (reacl/return :app-state state)))
