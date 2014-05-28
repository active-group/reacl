# Reacl

A ClojureScript library for programming with Facebook's React
framework.  This is very different from David Nolen's Om framework.

## Using it

Your `project.clj` should contain something like this:

	  :dependencies [[org.clojure/clojure "1.6.0"]
					 [org.clojure/clojurescript "0.0-2173" :scope "provided"]
					 [reacl "0.1.0"]]

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

- global *application state* that is the same at all components, and
  can only be manipulated as a whole
- *local state* of a component, used to maintain GUI state not already
  tracked by the DOM
- *arguments* passed from a component to its sub-components

(These can all be arbitrary ClojureScript objects, and are not
restricted to JavaScript hashmaps.)

When a component wants to manipulate the application state, that
typically happens inside an event handler: That event handler must
simply return a new application state.  Similarly, if an event
handler wants to change the local state, it returns a new one.

The key to tying these aspects together are the `reacl.core/defclass`
macro for defining a Reacl class (just a React component class, but
implementing Reacl's internal protocols), and the
`reacl.core/event-handler` function for implementing "functional event
handlers".

In addition to this core model, Reacl also provides a convenience
layer on React's virtual dom (which can be used independently), as
well as a lens library for access into application state (which can
also be used independently).

## Organization

Reacl consists of three namespaces:

- `reacl.core` with Reacl's core programming model
- `reacl.dom` for conveniently constructing virtual DOM nodes in
  ClojureScript
- `reacl.lens` for access into the application state with lenses

The `reacl.dom` and `reacl.lens` namespaces can be used independently.
While `reacl.core` depends on `reacl.dom`, it could also be used
directly with React's virtual-DOM API or other DOM binding.  The
`reacl.lens` namespace contains a completely independent, fairly
generic implementation of lenses.

## Example

Check out the very simple example for managing a to-do list in file
[examples/todo/core.cljs](examples/todo/core.cljs)
(don't
expect [TodoMVC](http://todomvc.com/)).  We use this namespace header:

    (ns examples.products.core
      (:require [reacl.core :as reacl :include-macros true]
                [reacl.dom :as dom :include-macros true]))


First of all, we define a record type for to-dos, with a descriptive
text and a flag indicating that it's done:

    (defrecord Todo [text done?])
    
Here is a component class for a single item, which allows marking the
`done?` field as done:

    (reacl/defclass to-do-item
      todos [lens]
      render
      (fn [& {:keys [dom-node]}]
        (let [todo (lens/yank todos lens)]
          (dom/letdom
           [checkbox (dom/input
                      {:type "checkbox"
                       :value (:done? todo)
                       :onChange (fn [e]
                                   (on-check
                                    (.-checked (dom-node checkbox))))})]
           (dom/div checkbox
                    (:text todo)))))
      on-check
      (reacl/event-handler
       (fn [checked?]
         (reacl/return :app-state
                       (lens/shove todos
                                   (lens/in lens :done?)
                                   checked?)))))

The class is called `to-do-item`.  Within the component code, the
application state is accessible as `todos`, and the component accepts a
single parameter `lens`, which is a lens for accessing the to-do item
managed by this component within the application state.

The component class defines a `render` function.  Within that
function, we need access to the real DOM node of the checkbox, so we
bind the `dom-node` keyword parameter in the render function.

The code first extracts the to-do item managed by this component via
`lens/yank`, creates an `input` DOM element, binds it to `checkbox`
via `letdom` (because the event handler will want to access via
`dom-node`), and uses that to create a `div` for the entire to-do item.

The event handler specified as the `onChange` attribute extracts the
value of the checked flag, and calls the `on-check` event handler
specified later in the class definition.  That event handler is
specified using `reacl/event-handler`, which means that it's expected
to use `reacl/return` to construct a return value that whether there's
a new application state or component state.  In this case, there's no
component state, so the call to `reacl/return` only specifies a new
application state via the `:app-state` keyword argument.  The new
application state uses `lens` to replace the `done?` flag.

Here's the todo application for managing a list of `to-do-item`s:

    (reacl/defclass to-do-app
      todos []
      render
      (fn [& {:keys [local-state instantiate]}]
        (dom/div
         (dom/h3 "TODO")
         (dom/div (map-indexed (fn [i todo]
                                 (dom/keyed (str i) (instantiate to-do-item (lens/at-index i))))
                               todos))
         (dom/form
          {:onSubmit handle-submit}
          (dom/input {:onChange on-change :value local-state})
          (dom/button
           (str "Add #" (+ (count todos) 1))))))

      initial-state ""

      on-change
      (reacl/event-handler
       (fn [e state]
         (reacl/return :local-state (.. e -target -value))))

      handle-submit
      (reacl/event-handler
       (fn [e _ text]
         (.preventDefault e)
         (reacl/return :app-state (concat todos [(Todo. text false)])
                       :local-state ""))))

Since this component will need to instantiate `to-do-item`
subcomponent, it binds `instantiate` in the render method.  Moreover,
to help React identify the individual to-do items in the list, it uses
a list of `dom/keyed` elements that attach string keys to the
individual items.

This component does have a component state, namely the description of
the to-do item that's being entered.  `initial-state` specifies it as
initially empty, and then the `on-change` event-handler specifies a
new value via the `:local-state` keyword argument to `reacl/return`.

The `handle-submit` event handler adds a new to-do item, so it
specifies both a new empty description text and a new application
state.

That's it.  Hopefully that's enough to get you started.  Be sure to
also check out the [`products`
example](examples/products/core.cljs).

## License

Copyright © 2014 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
