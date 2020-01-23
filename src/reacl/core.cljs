(ns ^{:no-doc true
      :doc "Reacl core functionality."}
  reacl.core
  (:require [reacl2.core :as reacl2]))

(def ^:static no-reaction reacl2/no-reaction)

(def ^:static pass-through-reaction reacl2/pass-through-reaction)

(def ^:static reaction reacl2/reaction)

(def ^:static invoke-reaction reacl2/invoke-reaction)

(def ^:static react-class reacl2/react-class)

(def ^:static has-class? reacl2/has-class?)

(def ^:static opt reacl2/opt)

(def ^:static instantiate-toplevel reacl2/instantiate-toplevel)

(defprotocol ^:no-doc IReaclView
  (-instantiate [clazz args]))

(defn render-component
  [element clazz & args]
  (apply reacl2/render-component element clazz args))

(def ^:static return reacl2/return)

(def ^:static send-message! reacl2/send-message!)

(defn ^:no-doc class->view
  [clazz]
  (let [react-class (reacl2/react-class clazz)]
    (reify
      IFn
      (-invoke [this]
        (-instantiate this []))
      (-invoke [this a1]
        (-instantiate this [a1]))
      (-invoke [this a1 a2]
        (-instantiate this [a1 a2]))
      (-invoke [this a1 a2 a3]
        (-instantiate this [a1 a2 a3]))
      (-invoke [this a1 a2 a3 a4]
        (-instantiate this [a1 a2 a3 a4]))
      (-invoke [this a1 a2 a3 a4 a5]
        (-instantiate this [a1 a2 a3 a4 a5]))
      (-invoke [this a1 a2 a3 a4 a5 a6]
        (-instantiate this [a1 a2 a3 a4 a5 a6]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20]
        (-instantiate this [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20]))
      (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest]
        (-instantiate this (concat [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20] rest)))
      IReaclView
      (-instantiate [this args]
        (reacl2/instantiate-embedded-internal-v1 clazz nil reacl2.core/no-reaction nil args))
      reacl2/IReaclClass
      (-has-app-state? [this] false)
      (-react-class [this] react-class)
      (-instantiate-embedded-internal [this args]
        (reacl2/instantiate-embedded-internal-v1 clazz nil reacl2.core/no-reaction nil args))
      (-validate! [this app-state args]
        nil)
      )))
