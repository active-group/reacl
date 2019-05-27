(ns reacl2.test-util.beta
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom]
            cljsjs.react.test-renderer))

;; TODO: xpath/select adaptor in the test utility object (when mounted)?

;; TODO: utils that  are handy.  util/click-element from Simon.

(defrecord ^:private TestEnv [class has-app-state? renderer ret-atom])

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

(defn- test* [class has-app-state?]
  (TestEnv. class has-app-state?
            (js/ReactTestRenderer.create nil nil)
            (atom (reacl/return))))

(defn fn-env
  "Returns a test environment for tests on the given function (which
  should return a dom element or class instance) or non-app-state
  class."
  [f]
  (assert (or (not (reacl/reacl-class? f))
              (not (reacl/has-app-state? f))))
  (test* f false))

(defn env
  "Returns a fresh test environment for testing the given class."
  [class]
  (if (and (reacl/reacl-class? class)
           (not (reacl/has-app-state? class)))
    (fn-env class)
    (test* class true)))

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

(defn mount!
  ^{:arglists '([env app-state & args]
                [env & args])
    :doc "Does a fresh mount of the class from the given testing
  environment with the given app-state and args, returning any
  app-state changes and actions returned by the class in form of a
  `reacl/return` value."}
  [env & args]
  (render env nil) ;; clean up to make it a mount even if called twice.
  (render-return env (apply instantiate env args)))

(defn is-mounted?
  "Returns if the given testing environment contains a mounted component (true between [[mount!]] and [[unmount!]])."
  [env]
  (some? (find-component env)))

(defn get-component
  "Return the component currently mounted in the given test
  environment. Throws if it is not mounted."
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

(defn mount
  "Mounts an instance of the given class into a fresh test environment, and returns the component."
  [class & args]
  (let [[ret comp] (apply mount* class args)]
    (when (not= ret (reacl/return))
      (js/console.warn "Mounting the tested class %s returned (%o). Use mount-ignore, or env and mount! instead." (reacl/class-name class) ret))
    comp))

(defn mount-ignore
  "Mounts an instance of the given class into a fresh test
  environment, and returns the component. This is the same
  as [[mount]], but does not issue a warning when the class returned
  something on mount."
  [class & args]
  (second (apply mount* class args)))

(defn ^{:arglists '([comp] [env])} with-component-return
  "Calls `(f comp & args)` if called with a component, or if called
  with a test environment, with the currently mounted toplevel class
  instance. In any case, after `f` has been evaluated for its
  side-effects, this returns what has been returned from the tested
  toplevel class in the form of a `reacl/return` value."
  [c f & args]
  (let [env (find-env c)]
    (with-collect-return! env
      (fn []
        (apply f (get-component* c)
               args)))))

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

(defn ^{:arglists '([comp] [env])} inspect-local-state
  "Return the current local-state of a component, or if given a test
  environment, of the toplevel component. Throws if it is not
  mounted."
  [c]
  (reacl/extract-local-state (get-reacl-instance c)))

(defn ^{:arglists '([comp] [env])} send-message!
  "Sends the given message to the given component, or the component
  currently mounted in the given test environment. Returns a changed
  app-state and actions from the toplevel class, in the form of a
  `reacl/return` value. Throws if it is not mounted."
  [c msg]
  (with-reacl-instance-return c
    (fn [inst]
      (reacl/send-message! inst msg))))

(defn invoke-callback!
  "Invokes the function assiciated with the given `callback` of the
  given dom element (e.g. `:onclick`) with the given event object, and
  returns a changed app-state and actions from the toplevel class, in
  the form of a `reacl/return` value.

  Note that this does not do a 'real' DOM event dispatching, e.g. no
  bubbling or canceling phase, and no default effects."
  [elem callback event]
  (with-collect-return! (find-env elem)
    (fn []
      (let [n (aget dom/reacl->react-attribute-names (name callback))]
        ((aget (.-props elem) n) event)))))

;; click-element? change-element? some very common event objects?

(defn ^{:arglists '([comp app-state & args]
                    [comp & args]
                    [env app-state & args]
                    [env & args])}
  update!
  "Performs an update of the given toplevel component, or the toplevel
  component currently mounted in the given test environment. Returns a
  changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."
  [c & args]
  (let [env (get-env c)]
    (render-return env (apply instantiate env args))))

(defn unmount!
  "Performs an unmount of component mounted in the given test
  environment. Returns a changed app-state and actions from the class,
  in the form of a `reacl/return` value."
  [env]
  (when-not (is-mounted? env)
    (throw (js/Error. "Something must be mounted to be unmounted. Call mount! first.")))
  (render-return env nil))

(defn inject-return!
  "This injects or simulates a [[reacl/return]] from a method of the
  class the given component was instantied from. Returns a
  changed app-state and actions from the toplevel class, in the form
  of a `reacl/return` value."
  [comp ret]
  (assert (reacl/returned? ret))
  (let [instance (.-instance comp)
        class (reacl/component-class instance)]
    (when-not (reacl/reacl-class? class)
      (throw (ex-info "The given component is not an instance of a reacl class." {:value comp})))
    (assert (or (reacl/keep-state? (reacl/returned-app-state ret))
                (reacl/has-app-state? class)))
    (with-collect-return! (find-env comp)
      (fn []
        (reacl/handle-returned! instance ret 'injected)))))

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

(defn ^{:arglists '([comp state] [env state])} inject-local-state!
  "Sets the local-state of a component, or the toplevel component
  currently mounted in the given test environment. Returns a changed
  app-state and actions returned from the toplevel class as a result,
  in the form of a `reacl/return` value. Throws if it is not mounted.

  Note that it is a bit dangerous to base tests on this, and should
  only be used with care, if the way to get to this state is otherwise
  impossible in a unit test."
  [c state]
  ;; these should be equivalent:
  #_(reacl/set-local-state! (get-reacl-instance tc) state)
  (inject-return! (get-component* c) (reacl/return :local-state state)))
