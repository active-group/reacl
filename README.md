# Reacl

A ClojureScript library for programming with Facebook's React
framework.  This is very different from David Nolen's Om framework.

## Using it

Your `project.clj` should contain something like this:

	  :dependencies [[org.clojure/clojure "1.6.0"]
					 [org.clojure/clojurescript "0.0-2173" :scope "provided"]
					 [reacl "0.3.0"]]

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
typically happens inside an event handler: That event handler should
send a *message* to the component - a value communicating what just
happened.  The component can declare a *message handler* that receives
the message, and returns a new application state and/or new local
state.

The key to tying these aspects together are the `reacl.core/defclass`
macro for defining a Reacl class (just a React component class, but
implementing Reacl's internal protocols), as the
`reacl.core/instantiate` function for making a component object from a
class, and the `reacl.core/send-message!` for sending a message to a
component.

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

	(ns examples.todo.core
	  (:require [reacl.core :as reacl :include-macros true]
				[reacl.dom :as dom :include-macros true]
				[reacl.lens :as lens]))

First of all, we define a record type for to-dos, with a descriptive
text and a flag indicating that it's done:

    (defrecord Todo [text done?])
    
Here is a component class for a single item, which allows marking the
`done?` field as done:

	(reacl/defclass to-do-item
	  this todos [lens]
	  render
	  (let [todo (lens/yank todos lens)]
		(dom/letdom
		 [checkbox (dom/input
					{:type "checkbox"
					 :value (:done? todo)
					 :onChange #(reacl/send-message! this
													 (.-checked (dom/dom-node this checkbox)))})]
		 (dom/div checkbox
				  (:text todo))))
	  handle-message
	  (fn [checked?]
		(reacl/return :app-state
					  (lens/shove todos
								  (lens/in lens :done?)
								  checked?))))

The class is called `to-do-item`.  Within the component code, the
component is accessible as `this`, the application state is accessible
as `todos`, and the component accepts a single parameter `lens`, which
is a lens for accessing the to-do item managed by this component
within the application state.

The component class defines a `render` expression.  The code first
extracts the to-do item managed by this component via `lens/yank`,
creates an `input` DOM element, binds it to `checkbox` via
`dom/letdom` (because the event handler will want to access via
`dom/dom-node`), and uses that to create a `div` for the entire to-do
item.

The message handler specified as the `onChange` attribute extracts the
value of the checked flag, and sends it as a message to the component
`this` with the `reacl/send-message` function.

That message eventually ends up in the `handle-message` function,
which is expected to use `reacl/return` to construct a return value
that communicates whether there's a new application state or component
state.  In this case, there's no component state, so the call to
`reacl/return` only specifies a new application state via the
`:app-state` keyword argument.  The new application state uses `lens`
to replace the `done?` flag.

Here's the todo application for managing a list of `to-do-item`s:

	(defrecord New-text [text])
	(defrecord Submit [])

	(reacl/defclass to-do-app
	  this todos local-state []
	  render
	  (dom/div
	   (dom/h3 "TODO")
	   (dom/div (map-indexed (fn [i todo]
							   (dom/keyed (str i) (reacl/instantiate to-do-item this (lens/at-index i))))
							 todos))
	   (dom/form
		{:onSubmit (fn [e _]
					 (.preventDefault e)
					 (reacl/send-message! this (Submit.)))}
		(dom/input {:onChange (fn [e]
								(reacl/send-message! this
													 (New-text. (.. e -target -value))))
					:value local-state})
		(dom/button
		 (str "Add #" (+ (count todos) 1)))))

	  initial-state ""

	  handle-message
	  (fn [msg]
		(cond
		 (instance? New-text msg)
		 (reacl/return :local-state (:text msg))

		 (instance? Submit msg)
		 (reacl/return :local-state ""
					   :app-state (concat todos [(Todo. local-state
					   false)])))))
				   
To help React identify the individual to-do items in the list, it uses
a list of `dom/keyed` elements that attach string keys to the
individual items.

This component supports two different user actions: By typing, the
user submits a new description for the to-do item in progress.  That
encoded with a `New-text` message.  Also, the user can press return or
press the `Add` button, which submits the current to-do item.  The
event handlers for these actions send these objects as messages.

This component does have a component state, namely the description of
the to-do item that's being entered.  `initial-state` specifies it as
initially empty, and then the `handle-message` function specifies a
new value via the `:local-state` keyword argument to `reacl/return`.
Upon submit, the message handler returns a new empty-text description,
and a new app state consisting of the to-to items with the new one
appended.

That's it.  Hopefully that's enough to get you started.  Be sure to
also check out the [`products` example](examples/products/core.cljs)
or the [`comments´ example](examples/comments/core.cljs)

## License

Copyright Â© 2014 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
