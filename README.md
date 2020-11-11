<img src="https://raw.githubusercontent.com/active-group/reacl/master/logo.png" width="180">

A ClojureScript library for programming with Facebook's React framework.

[![Clojars Project](https://img.shields.io/clojars/v/reacl.svg)](https://clojars.org/reacl)
[![cljdoc reacl badge](https://cljdoc.xyz/badge/reacl)](https://cljdoc.xyz/d/reacl/reacl/CURRENT)
[![Actions Status](https://github.com/active-group/reacl/workflows/Tests/badge.svg)](https://github.com/active-group/reacl/actions)

## Using it

Your `project.clj` should contain something like this:

```clj
:dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
               [org.clojure/clojurescript "1.9.293" :scope "provided"]
               [reacl "2.2.8"]]
```

## Documentation

API documentation for the latest release: [CljDoc](https://cljdoc.xyz/d/reacl/reacl/CURRENT).

An introduction to the library can be found [here](doc/intro.md).

And the sources on [Github](http://active-group.github.io/reacl/).

## Running the tests

The following commands run the tests defined in `test-dom` and `test-nodom`,
respectively. To execute them, [karma](https://github.com/karma-runner/karma) is needed.

```
lein doo chrome-headless test-dom
lein doo chrome-headless test-nodom
```

You may substitute `chrome-headless` with a runner of your choice.

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

## Examples

There are several examples in the [`examples/`](examples/)
subdirectory, like an app managing a to-do list:
[`examples/todo/`](examples/todo/) (don't expect
[TodoMVC](http://todomvc.com/)), an example with a searchable product
catalogue: [`examples/products/`](examples/products), and an example
involving communicatinon with a server:
[`examples/comment`](examples/comment).

And of course the [`clock` example](examples/clock), which the
[introduction](doc/intro.md) tutorial explains step by step.

You can run the examples and edit the code live by running `lein fig`
from the console, and open
`http://localhost:9500/figwheel-extra-main/todo` for the todo example,
and the others accordingly.

## License

Copyright © 2015-2020 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
