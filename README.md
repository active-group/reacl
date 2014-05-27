# Reacl

A ClojureScript library for programming with Facebook's React
framework.  This is very different from David Nolen's Om framework.

## Reacl Model

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

## Reacl organization

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

## License

Copyright © 2014 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
