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

(def ^:static render-component reacl2/render-component)

(def ^:static return reacl2/return)

(def ^:static send-message! reacl2/send-message!)
