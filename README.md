<img src="https://raw.githubusercontent.com/markusschlegel/reacl/master/logo.png" width="180">

A ClojureScript library for programming with Facebook's React framework.

[![Clojars Project](https://img.shields.io/clojars/v/reacl.svg)](https://clojars.org/reacl)
[![cljdoc reacl badge](https://cljdoc.xyz/badge/reacl)](https://cljdoc.xyz/d/reacl/reacl/CURRENT)
[![Build Status](https://travis-ci.org/active-group/reacl.svg?branch=master)](https://travis-ci.org/active-group/reacl)

## Using it

Your `project.clj` should contain something like this:

```clj
:dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
               [org.clojure/clojurescript "1.9.293" :scope "provided"]
               [reacl "2.1.3"]]
```

## API Documentation

For the latest release: [CljDoc](https://cljdoc.xyz/d/reacl/reacl/CURRENT).

And the sources on [Github](http://active-group.github.io/reacl/).

## Rationale

Facebook’s React framework brought a wonderful programming model to user interface development on the web. With React, your UI is the result of a pure function application with your application state as input. A change in your app state signifies advancement of (logical) time. At every point in logical time your UI is (conceptually) entirely rerendered.

<img src="https://raw.githubusercontent.com/markusschlegel/reacl/master/rationale-2.png" width="524">

With React, the transitions in logical time (a.k.a. your business logic) are driven and managed implicitely by imperative calls to `setState`. Reacl improves on this model by decoupling the triggering of change (`send-message!`) from the pure handling of application state transitions (`handle-message`).

<img src="https://raw.githubusercontent.com/markusschlegel/reacl/master/rationale-3.png" width="609">

Advancement of logical time is now driven by calls to `send-message!`. The messages you send are then handled by the components in their `handle-message` functions, which are functionally pure descriptions of your business logic. The messages encode the change that happens in your application as values. This leads to good design, ease of reasoning, and general peace of mind.


## The data flow model

React components can pass data from parent to children via props. There is no standard way to pass data the other way. To circumvent this shortcoming, you could pass down callback functions that call `setState` on the parent. This callback model is brittle and error-prone.

<img src="https://raw.githubusercontent.com/active-group/reacl/master/react.png" width="160">

In an attempt to fix the React model, Redux and similar frameworks like re-frame and Om have a global application store that you can use to structure your app. With this model, data always flows through a central node.

<img src="https://raw.githubusercontent.com/active-group/reacl/master/redux.png" width="160">

The problem with this model is that components are no longer composable by default. Making components compose is hard work. You have to allocate storage in the global application store manually such that two components of the same kind don't interfere. This is because writing to the global store is essentially a side-effect.

Reacl has a different model of passing data rootwards. Just as with React, data flows leafwards via simple props. In addition, data can flow rootwards via `return :app-state`.

<img src="https://raw.githubusercontent.com/active-group/reacl/master/reacl.png" width="160">

Components are therefore composable by default, because each parent has full control over what it passes down to its children and how it reacts to state changes.


## Organization

Reacl consists of two namespaces:

- `reacl2.core` with Reacl's core programming model
- `reacl2.dom` for conveniently constructing virtual DOM nodes in
  ClojureScript

The `reacl2.dom` namespace can be used independently.
While `reacl2.core` depends on `reacl2.dom`, it could also be used
directly with React's virtual-DOM API or other DOM binding.


## Reacl components

A minimal Reacl component consists of a name, some *application state*, some *arguments*, and a `render` function.

```clj
(reacl/defclass clock
  this       ;; A name for when you want to reference the current component
  app-state  ;; A name for this components application state
  [greeting] ;; Arguments

  render
  (dom/div
    (dom/h1 (str greeting ", world!"))
    (dom/h2 (str "It is " (.toLocaleTimeString (:date app-state)) "."))
    (dom/p (str "Number of ticks: " (:ticks app-state)))))

(reacl/render-component
  (.getElementById js/document "editor")
  clock
  {:date (js/Date.)
   :ticks 42}
  "Hello")
```

The `render` clause lets you define a function going from your components app state to a virtual DOM tree. So far this is mostly a 1-to-1 translation of the [corresponding React component.](https://reactjs.org/docs/state-and-lifecycle.html)

We now want this clock component to update every second. With React you would start a timer that called a method of your component and in that method you would call `setState` which in turn triggered the component to rerender. In contrast, with Reacl, the timer merely *sends a message* to the component. The message holds a representation of the action that occurred: the advancement of time. This forces you to think about the actions in your application as values. It also decouples triggers (the impure world) and your business logic (the pure world).

```clj
(defrecord Tick [date])
```

You can set up the timer in a `component-did-mount` clause. You use `send-message!` to send a message to a component.

```clj
...
  component-did-mount
  (fn []
    (let [timer (.setInterval
                  js/window
                  #(reacl/send-message! this (->Tick (js/Date.)))
                  1000)]))
...
```

Every 1000ms the component receives the message. The message handler defined in the `handle-message` clause has to compute a new application state depending on the contents of the message and its current app state.

```clj
...
  handle-message
  (fn [msg]
    (reacl/return :app-state
                  (assoc app-state
                         :date
                         (:date msg)
                         :ticks
                         (+ 1 (:ticks app-state)))))
...
```

The component is now re-rendered with the new app state. Notice how we never set any state explicitly by calling something like `setState`. We only sent a message. The component’s `handle-message` function is a functionally pure description of your business logic.

### Application state and composition

Components can easily be composed. You just use a component's name as a function when you construct a virtual DOM tree.

```clj
(reacl/defclass two-clocks
  this app-state []
  render
  (dom/div
    {:class "clocks"}
    (clock
      {:date (js/Date.)
       :ticks 0}
      "Ciao")
    (clock
      {:date (js/Date.)
       :ticks 1000}
      "Gruezi")))
```

Here we define a component that holds two clocks. The two clocks work completely independently. Each component's application state (the date and the number of ticks) is only directly accessible within the component itself. There is no global application state. This makes composition a lot easier because you don't have to think about the allocation of resources inside a global store.

### Local state

App state represents something that's important to your application. In some situations you need a different kind of state, something that's just an aspect of the GUI but not yet tracked by the DOM. Therefore Reacl provides you with the notion of *local state*.

So far in our clock example we start a timer when the component mounts but we don't stop it when the component unmounts. In order to fix this problem, we can save the timer ID as local state inside our component in order to reference it in a `component-will-unmount` lifecycle clause. We introduce local state to our component class with the `local-state` clause.

```clj
...
  local-state [timer nil] ;; We refer to our local state as `timer`. Its default value is nil.

  component-did-mount
  (fn []
    (let [timer (.setInterval
                  js/window
                  #(reacl/send-message! this (->Tick (js/Date.)))
                  1000)]
      (reacl/return :local-state
                    timer)))

  component-will-unmount
  (fn []
    (.clearInterval
      js/window
      timer))
...
```

### Reactions and `:embed-app-state`

TODO

### Actions for side-effects

TODO

## Model

The central idea of Reacl is this: Reacl programs are pure.  They
(mostly) do not need to call imperative functions to get anything
done.  This goes well with React's philosophy, but goes a step further
as well as making programming more convenient.

This idea has immediate consequences on how Reacl organizes data that
is communicated between components.  In React, a component has
*properties* and *state*, where properties typically carry arguments
from one component to its subcomponents.

In Reacl, there are three different kinds of data:

- *application state* managed by a component that represents something
  that's important to your application, rather than just being an
  aspect of the GUI
- *local state* of a component, used to maintain GUI state not already
  tracked by the DOM
- *arguments* passed from a component to its sub-components

(These can all be arbitrary ClojureScript objects, and are not
restricted to JavaScript hashmaps.)

When a component wants to manipulate the application state, that
typically happens inside an event handler: That event handler should
send a *message* to the component - a value communicating what just
happened.  The component can declare a *message handler* that receives
the message, and returns a new application state and/or new local
state.

The key to tying these aspects together are the `reacl2.core/defclass`
macro for defining a Reacl class (just a React component class, but
implementing Reacl's internal protocols), and the
`reacl2.core/send-message!` for sending a message to a component.

In addition to this core model, Reacl also provides a convenience
layer on React's virtual dom. (The convenience layer can be used
independently; also, any other convenience layer over React's virtual
dom should be usable with Reacl.)

## Example

Check out the very simple example for managing a to-do list in file
[examples/todo/core.cljs](examples/todo/core.cljs)
(don't
expect [TodoMVC](http://todomvc.com/)).  We use this namespace header:

```clj
(ns examples.todo.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))
```

First of all, we define a record type for to-dos, with a unique id
(needed to identify items to be deleted), a descriptive text and a
flag indicating that it's done:

```clj
(defrecord Todo [id text done?])
````

Here is a component class for a single item, which allows marking the
`done?` field as done and deleting that item:

```clj
(reacl/defclass to-do-item
  this todo [parent]
  render
  (dom/letdom
   [checkbox (dom/input
              {:type "checkbox"
               :value (:done? todo)
               :onchange #(reacl/send-message! this
                                               (.-checked (dom/dom-node this checkbox)))})]
   (dom/div checkbox
            (dom/button {:onclick #(reacl/send-message! parent (Delete. todo))}
                        "Zap")
            (:text todo)))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (assoc todo :done? checked?))))
```

The class is called `to-do-item`.  Within the component code, the
component is accessible as `this`, the component's application state
is accessible as `todo`.  Also, the component representing list of
todo items that this item is a part of will be passed as an argument,
accessible via the parameter `parent`.

The component class defines a `render` expression.  The code
creates an `input` DOM element, binds it to `checkbox` via
`dom/letdom` (because the event handler will want to access via
`dom/dom-node`), and uses that to create a `div` for the entire to-do
item.

The message handler specified as the `onChange` attribute extracts the
value of the checked flag, and sends it as a message to the component
`this` with the `reacl/send-message` function.

That message eventually ends up in the `handle-message` function,
which is expected to use `reacl/return` to construct a return value
that communicates whether there's a new application state or local
state.  In this case, there's no local state, so the call to
`reacl/return` only specifies a new application state via the
`:app-state` keyword argument.

There is also an event handler attached to a `Zap` button, which is
supposed to delete the item.  For this, the component needs the help
of the parent component that manages the list of components - only the
parent component can remove the todo item from the list.  The message
it sends to `parent` is made from this record type:

```clj
(defrecord Delete [todo])
```

We'll need to handle this type of message in the class of the parent
component.

For the list of todo items, we define a record type for the entire list of todos managed by
the application, in addition to the id to be used for the next item:

```clj
(defrecord TodosApp [next-id todos])
```

The list component accepts the following messages in adddition to `Delete`:

```clj
(defrecord New-text [text])
(defrecord Submit [])
(defrecord Change [todo])
```

The `New-text` message says that the user has changed the text in the
input field for the new todo item.  The `Submit` button says that the
user has pushed the `Add` button or pressed return to register a new
todo item.  The `Change` item says that a particular todo item has
changed in some way - this will be sent when the user checks the
"done" checkbox.

The `to-do-app` class manages both *app state* - the `TodosApp` object
- and transient local state, the text the user is entering but has not
completed yet.  Here is the header of the class, along with the render
method:

```cljs
(reacl/defclass to-do-app
  this app-state []
  local-state [local-state ""]
  render
  (dom/div
   (dom/h3 "TODO")
   (dom/div (map (fn [todo]
                   (dom/keyed (str (:id todo))
                              (to-do-item
			       (reacl/opt :reaction (reacl/reaction this ->Change))
                               todo
                               this)))
                 (:todos app-state)))
   (dom/form
    {:onsubmit (fn [e _]
                 (.preventDefault e)
                 (reacl/send-message! this (Submit.)))}
    (dom/input {:onchange 
                (fn [e]
                  (reacl/send-message!
                   this
                   (New-text. (.. e -target -value))))
                :value local-state})
    (dom/button
     (str "Add #" (:next-id app-state)))))
```

To help React identify the individual to-do items in the list, it uses
a list of `dom/keyed` elements that attach string keys to the
individual items.

Each `to-do-item` component is instantiated by calling the class as a function,
passing its app state (the individual todo item), and
any further arguments - in this case `this` is passed for
`to-do-item`'s `parent` parameter.  The `reacl/opt` argument provides
options for its call, in this case specifying a *reaction*.

The reaction is a slightly restricted version of a callback that gets
invoked whenever the component's app state changes.  (Remember that a
todo item's app state changes when the user toggles the done checkbox.)  The
reaction here `(reacl/reaction this ->Change)` - says that a message
should be sent to `this`, the `to-do-app` component, and that the
app-state (the todo item) should be wrapped using the `->Change`
constructor.  So, a `Change` message is sent to the `to-do-app`
component whenever an individual todo item changes.

This component supports two different user actions: By typing, the
user submits a new description for the to-do item in progress.  That
is encoded with a `New-text` message.  Also, the user can press return
or press the `Add` button, which submits the current to-do item in a
`Submit` message.  The event handlers for these actions send these
objects as messages.

The `to-do-app` class has more clauses in addition to render.

The `local-state` clauses gives the local state a name (in this case,
`local-state`), and the expression `""` initializes the text entered by the
user to the empty string:

The `handle-message` function finally handles all the different
message types.  The `New-text` message leads to a new local state
being returned:

```clj
  handle-message
  (fn [msg]
    (cond
     (instance? New-text msg)
     (reacl/return :local-state (:text msg))
```

A `Submit` message leads to the text field to be cleared and the app
state to be augmented by the new todo item, generating a fresh id:

```clj
     (instance? Submit msg)
     (let [next-id (:next-id app-state)]
       (reacl/return :local-state ""
                     :app-state
                     (assoc app-state
                       :todos
                       (concat (:todos app-state)
                               [(Todo. next-id local-state false)])
                       :next-id (+ 1 next-id))))
```

The `Delete` message sent by an item prompts the message handler to
remove that item from the list:

```clj
     (instance? Delete msg)
     (let [id (:id (:todo msg))]
       (reacl/return :app-state
                     (assoc app-state
                       :todos 
                       (remove (fn [todo] (= id (:id todo)))
                               (:todos app-state)))))
```

Finally, the `Change` message leads to the todo item in question to be
replaced by the new version:

```clj
     (instance? Change msg)
     (let [changed-todo (:todo msg)
           changed-id (:id changed-todo)]
       (reacl/return :app-state
                     (assoc app-state
                       :todos (mapv (fn [todo]
                                      (if (= changed-id (:id todo) )
                                        changed-todo
                                        todo))
                                    (:todos app-state))))))))
```

That's it.  Hopefully that's enough to get you started.  Be sure to
also check out the [`products` example](examples/products/core.cljs)
or the [`comments` example](examples/comments/core.cljs)

## Running the tests

The following commands run the tests defined in `test-dom` and `test-nodom`,
respectively. To execute them, [karma](https://github.com/karma-runner/karma) is needed.

```
lein doo chrome-headless test-dom
lein doo chrome-headless test-nodom
```

You may substitute `chrome-headless` with a runner of your choice.

## License

Copyright © 2015-2017 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
