(ns reacl2.test-util.beta
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
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

(defrecord ^:private TestClass [class renderer ret-atom ref-atom])

(reacl/defclass ^:private runner-class this [class app-state args ret-atom ref-atom]
  refs [comp]
  
  render (let [red-act (fn [_ action] ;; TODO: static fn!?
                         (swap! ret-atom reacl/merge-returned (reacl/return :action action))
                         (reacl/return))]
           (apply class
                  (if (reacl/has-app-state? class)
                    (reacl/opt :ref comp :reduce-action red-act :reaction (reacl/pass-through-reaction this))
                    (reacl/opt :ref comp :reduce-action red-act))
                  (if (reacl/has-app-state? class)
                    (cons app-state args)
                    args)))

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

(defn- instantiate [tc & args]
  (assert (instance? TestClass tc))
  (let [class (:class tc)
        [app-state args] (if (reacl/has-app-state? class)
                           [(first args) (rest args)]
                           [nil args])
        ret-atom (:ret-atom tc)
        ref-atom (:ref-atom tc)]
    (reacl/instantiate-toplevel runner-class
                                class
                                app-state args
                                ret-atom
                                ref-atom)))

(defn test-class
  "Returns a utility object to test the given class."
  [class]
  (assert (reacl/reacl-class? class))
  (TestClass. class
              (js/ReactTestRenderer.create nil nil)
              (atom (reacl/return))
              (atom nil)))

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
  (render-return tc (apply instantiate tc args)))

(defn unmount!
  "Performs an unmount of component in the given test utility
  object. Returns a changed app-state and actions returned from the
  class as a result, in form of a `reacl/return` value."
  [tc]
  (when-not (is-mounted? tc)
    (throw (js/Error. "Test component must be mounted to be unmounted.")))
  (render-return tc nil))
