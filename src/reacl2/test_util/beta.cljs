(ns reacl2.test-util.beta
  (:require [reacl2.core :as reacl :include-macros true]
            cljsjs.react.test-renderer))

(comment
  ;; Idea:

  (let [c (test-class my-class)]
    ;; Note: in all cases, a returned local-state is 'applied' and removed from the result. Same for messages.
    (mount! c app-state & args) => (reacl/return)

    ;; maybe:
    (inject-local-state! c new-local-state)
    (inspect-local-state c) => local-state

    (send-message! c ...) => (reacl/return ...)
    (update! c new-app-state & new-args) => (reacl/return ...)

    (unmount! c) => (reacl/return)
    
    )

  ;; Note that the app-state is not supposed to be persisted after mount or send-message - only throuh update!
  ;; We could add a test-app with a persistent app-state - like via render-component (but maybe then it should not be returned from those send-message! and other.)

  )

(defrecord ^:private TestClass [class renderer ret-atom])

(reacl/defclass ^:private runner-class this [class args ret-atom]
  render (if (and (reacl/reacl-class? class)
                  (reacl/has-app-state? class))
           (apply class
                  (reacl/opt :reaction (reacl/pass-through-reaction this))
                  args)
           (apply class args))

  handle-message
  (fn [app-state]
    (swap! ret-atom reacl/merge-returned (reacl/return :app-state app-state))
    (reacl/return)))

(defn- instantiate [tc & args]
  (assert (instance? TestClass tc))
  (let [class (:class tc)
        ret-atom (:ret-atom tc)
        red-act (fn [_ action] ;; TODO: static fn!?
                  (swap! ret-atom reacl/merge-returned (reacl/return :action action))
                  (reacl/return))]
    (reacl/instantiate-toplevel runner-class
                                (reacl/opt :reduce-action red-act)
                                class
                                args
                                ret-atom)))

(defn- find-component [tc]
  ;; may return nil if class not mounted.
  (some-> (try (.-root (:renderer tc))
               ;; react throws if nothing is rendered; and I don't want to add an extra flag to know it.
               (catch :default e
                 nil))
          (.findByType (reacl/react-class runner-class))
          (.-children)
          (aget 0)))

(defn test* [class]
  (TestClass. class
              (js/ReactTestRenderer.create nil nil)
              (atom (reacl/return))))

(defn test-fn
  "Returns a utility object to test the given function or
  non-app-state class, which should return a dom element or class
  instance."
  [f]
  (assert (or (not (reacl/reacl-class? f))
              (reacl/has-app-state? f)))
  (test* f))

(defn test-class
  "Returns a utility object to test the given class."
  [class]
  (assert (reacl/reacl-class? class))
  (test* class))

(defn- with-collect-return! [tc f]
  (assert (instance? TestClass tc))
  (let [ret-atom (:ret-atom tc)]
    (reset! ret-atom (reacl/return))
    (f)
    @ret-atom))

(defn- render [tc elem]
  (assert (instance? TestClass tc))
  (.update (:renderer tc) elem))

(defn- render-return [tc elem]
  (with-collect-return! tc
    (fn []
      (render tc elem))))

(defn mount!
  ^{:arglists '([tc app-state & args]
                [tc & args])
    :doc "Mount the class of the given test utility object,
  returning any app-state changes and actions returned by the class in
  form of a `reacl/return` value."}
  [tc & args]
  (render tc nil) ;; clean up to make it a mount even if called twice.
  (render-return tc (apply instantiate tc args)))

(defn is-mounted?
  "Returns if the class tested with the given test utility object is currently mounted."
  [tc]
  (some? (find-component tc)))

(defn get-component
  "Return the component currently instantiated from the class of the
  given test utility object. Throws if it is not mounted."
  [tc]
  (or (find-component tc)
      (throw (js/Error. "Test component must be mounted."))))

(defn- get-component-instance [tc]
  (.-instance (get-component tc)))

(defn with-component-return
  "Do something with the component currently instantiated from the
  class of the given test utility object, by calling `(f comp &
  args)`, returning a changed app-state and actions returned from the
  class as a result, in form of a `reacl/return` value. Throws if it is
  not mounted."
  [tc f & args]
  (with-collect-return! tc
    (fn []
      (apply f (get-component tc) args))))

(defn inspect-local-state
  "Return the current local-state of the component currently
  instantiated from the class of the given test utility object. Throws
  if it is not mounted."
  [tc]
  (if (is-mounted? tc)
    (reacl/extract-local-state (get-component-instance tc))
    (throw (js/Error. "Test component must be mounted to inspect the local-state."))))

(defn inject-local-state!
  "Sets the local-state of the component currently instantiated from
  the class of the given test utility object. Returns a changed
  app-state and actions returned from the class as a result, in form
  of a `reacl/return` value. Throws if it is not mounted."
  [tc state]
  ;; Note: this uses reacl internals!
  (if (is-mounted? tc)
    (reacl/set-local-state! (get-component-instance tc) state)
    (throw (js/Error. "Test component must be mounted to inject a local-state."))))

(defn send-message!
  "Sends the given message to the component currently instantiated
  from the class of the given test utility object. Returns a changed
  app-state and actions returned from the class as a result, in form
  of a `reacl/return` value. Throws if it is not mounted."
  [tc msg]
  (if (is-mounted? tc)
    (reacl/send-message! (get-component-instance tc) msg)
    (throw (js/Error. "Test component must be mounted to send a message to it."))))

(defn ^{:arglists '([tc app-state & args]
                    [tc & args])}
  update!
  "Performs an update of the given test utility object to a class instance
  with the given app-state and arguments. Returns a changed app-state
  and actions returned from the class as a result, in form of a
  `reacl/return` value."
  [tc & args]
  (render-return tc (apply instantiate tc args)))

(defn unmount!
  "Performs an unmount of component in the given test utility
  object. Returns a changed app-state and actions returned from the
  class as a result, in form of a `reacl/return` value."
  [tc]
  (when-not (is-mounted? tc)
    (throw (js/Error. "Test component must be mounted to be unmounted.")))
  (render-return tc nil))
