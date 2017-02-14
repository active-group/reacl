(ns ^{:doc "Reacl core functionality."}
  reacl.core
  (:require [reacl2.core :as reacl2]
            [cljsjs.react]
            [cljsjs.react.dom]))

(def ^:static no-reaction reacl2/no-reaction)

(def ^:static pass-through-reaction reacl2/pass-through-reaction)

(def ^:static reaction reacl2/reaction)

(def ^:static invoke-reaction reacl2/invoke-reaction)

(def ^:static react-class reacl2/react-class)

(def ^:static has-class? reacl2/react-class)

(def ^:static opt reacl2/opt)

(def ^:static instantiate-toplevel reacl2/instantiate-toplevel)

(defprotocol ^:no-doc IReaclView
  (-instantiate [clazz args]))

(defn render-component
  [element clazz & args]
  (if (satisfies? IReaclView clazz)
    (js/ReactDOM.render
     (-instantiate clazz args)
     element)
    (apply reacl2/render-component element clazz args)))

(def ^:static return reacl2/return)

(def ^:static send-message! reacl2/send-message!)

(defn ^:no-doc class->view
  [clazz]
  (let [react-class (reacl2/react-class clazz)
        className (.-displayName react-class)
        error-reaction
        (fn [v]
          (throw (str "Error: " className " tried to return an app-state, but it is a view. Use defclass for programm elements with an app-state.")))]
    (reify
      IFn
      (-invoke [this & args]
        (-instantiate this args))
      IReaclView
      (-instantiate [this args]
        (reacl2/instantiate-embedded-internal-v1 clazz nil error-reaction args))
      reacl2/IReaclClass
      (-react-class [this] react-class)
      )))
