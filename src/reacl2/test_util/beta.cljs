(ns reacl2.test-util.beta
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            cljsjs.react.test-renderer))

(comment
  ;; Idea:

  (let-test-class [c (my-class ...)])

  (let [c (test-class my-class app-state & args)]
    ;; Note: in all cases, a returned local-state is 'applied' and removed from the result.
    (mount! c) => (reacl/return)

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

(defrecord ^:private TestState [app-state args])

(defrecord ^:private TestClass [class state renderer ret-atom ref-atom])

(reacl/defclass ^:private runner-class this [class app-state args ret-atom ref-atom]
  refs [comp]
  
  render (apply class
                (if (reacl/has-app-state? class)
                  (reacl/opt :ref comp :reaction (reacl/pass-through-reaction this))
                  (reacl/opt :ref comp))
                (if (reacl/has-app-state? class)
                  (cons app-state args)
                  args))

  component-did-mount
  (fn []
    (reset! ref-atom (reacl/get-dom comp))
    (reacl/return))

  component-did-update
  (fn []
    (reset! ref-atom (reacl/get-dom comp))
    (reacl/return))

  component-will-unmount
  (fn []
    (reset! ref-atom nil)
    (reacl/return))
  
  handle-message
  (fn [app-state]
    (swap! ret-atom reacl/merge-returned (reacl/return :app-state app-state))
    (reacl/return)))

(defn- instantiate [tc]
  (assert (instance? TestClass tc))
  (let [class (:class tc)
        ret-atom (:ret-atom tc)
        ref-atom (:ref-atom tc)
        state @(:state tc)]
    (reacl/instantiate-toplevel runner-class
                                (reacl/opt :reduce-action
                                           (fn [_ action] ;; TODO: static fn!?
                                             (swap! ret-atom reacl/merge-returned (reacl/return :action action))
                                             (reacl/return)))
                                class
                                (:app-state state)
                                (:args state)
                                ret-atom
                                ref-atom)))

(defn- test-class* [class app-state args]
  (assert (reacl/reacl-class? class))
  (TestClass. class (atom (TestState. app-state args))
              (js/ReactTestRenderer.create nil nil)
              (atom (reacl/return))
              (atom nil)))

(defn ^{:arglists '([tc app-state & args]
                    [tc & args])
        :doc "Returns a utility object to test the given class, with an initial app-state an arguments."}
  test-class
  [class & args]
  (if (reacl/has-app-state? class)
    (test-class* class (first args) (rest args))
    (test-class* class nil args)))

(defn- with-collect-return! [tc f]
  (assert (instance? TestClass tc))
  (let [ret-atom (:ret-atom tc)]
    (reset! ret-atom (reacl/return))
    (f)
    @ret-atom))

(defn mount!
  "Mount the class of the given test utility object,
  returning any app-state changes and actions returned by the class in
  form of a `reacl/return` value."
  [tc]
  (with-collect-return! tc
    (fn []
      (.update (:renderer tc) (instantiate tc)))))

(defn is-mounted?
  "Returns if the class tested with the given test utility object is currently mounted."
  [tc]
  (assert (instance? TestClass tc))
  (some? @(:ref-atom tc)))

(defn with-component
  "Do something with the component currently instantiated from the
  class of the given test utility object, by calling `(f comp &
  args)`, returning what `f` returns. Throws if it is not mounted."
  [tc f & args]
  (assert (instance? TestClass tc))
  (if-let [comp @(:ref-atom tc)]
    (apply f comp args)
    (throw (js/Error. "Test component must be mounted."))))

(defn with-component-return
  "Do something with the component currently instantiated from the
  class of the given test utility object, by calling `(f comp &
  args)`, returning a changed app-state and actions returned from the
  class as a result, in form of a `reacl/return` value. Throws if it is
  not mounted."
  [tc f & args]
  (with-component tc
    (fn [comp]
      (with-collect-return! tc
        (fn []
          (apply f comp args))))))

(defn inspect-local-state
  "Return the current local-state of the component currently
  instantiated from the class of the given test utility object. Throws
  if it is not mounted."
  [tc]
  (if (is-mounted? tc)
    (with-component tc
      reacl/extract-local-state)
    (throw (js/Error. "Test component must be mounted to inspect the local-state."))))

(defn inject-local-state!
  "Sets the local-state of the component currently instantiated from
  the class of the given test utility object. Returns a changed
  app-state and actions returned from the class as a result, in form
  of a `reacl/return` value. Throws if it is not mounted."
  [tc state]
  ;; Note: this uses reacl internals!
  (if (is-mounted? tc)
    (with-component tc
      reacl/set-local-state! state)
    (throw (js/Error. "Test component must be mounted to inject a local-state."))))

(defn send-message!
  "Sends the given message to the component currently instantiated
  from the class of the given test utility object. Returns a changed
  app-state and actions returned from the class as a result, in form
  of a `reacl/return` value. Throws if it is not mounted."
  [tc msg]
  (if (is-mounted? tc)
    (with-component-return tc
      reacl/send-message! msg)
    (throw (js/Error. "Test component must be mounted to send a message to it."))))

(defn ^{:arglists '([tc app-state & args]
                    [tc & args])}
  update!
  "Performs an update of the given test utility object to a class instance
  with the given app-state and arguments. Returns a changed app-state
  and actions returned from the class as a result, in form of a
  `reacl/return` value."
  [tc & args]
  (assert (instance? TestClass tc))
  (swap! (:state tc) (fn [st]
                       (if (reacl/has-app-state? (:class tc))
                         (assoc st
                                :app-state (first args)
                                :args (rest args))
                         (assoc st
                                :args args))))
  (with-collect-return! tc
    (fn []
      (.update (:renderer tc) (instantiate tc)))))

(defn unmount!
  "Performs an unmount of component in the given test utility
  object. Returns a changed app-state and actions returned from the
  class as a result, in form of a `reacl/return` value."
  [tc]
  (with-collect-return! tc
    (fn []
      (.update (:renderer tc) nil))))
