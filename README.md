# Reacl

A ClojureScript library for programming with Facebook's React
framework.  This is very different from David Nolen's Om framework.

## Using it

Your `project.clj` should contain something like this:

	  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                     [org.clojure/clojurescript "1.9.293" :scope "provided"]
					 [reacl "2.0.1"]]

## API Documentation

[Here](http://active-group.github.io/reacl/).

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

## Organization

Reacl consists of three namespaces:

- `reacl2.core` with Reacl's core programming model
- `reacl2.dom` for conveniently constructing virtual DOM nodes in
  ClojureScript

The `reacl2.dom` namespace can be used independently.
While `reacl2.core` depends on `reacl2.dom`, it could also be used
directly with React's virtual-DOM API or other DOM binding.

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

## License

Copyright © 2015-2017 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
