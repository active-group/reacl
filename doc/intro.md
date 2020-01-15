# Introduction to Reacl

## Rationale

Facebook’s React framework brought a wonderful programming model to user interface development on the web. With React, your UI is the result of a pure function application with your application state as input. A change in your app state signifies advancement of (logical) time. At every point in logical time your UI is (conceptually) entirely rerendered.

<img src="https://raw.githubusercontent.com/active-group/reacl/new-composability/doc/rationale-2.png" width="524">

With React, the transitions in logical time (a.k.a. your business logic) are driven and managed implicitely by imperative calls to `setState`. Reacl improves on this model by decoupling the triggering of change (`send-message!`) from the pure handling of application state transitions (`handle-message`).

<img src="https://raw.githubusercontent.com/active-group/reacl/new-composability/doc/rationale-3.png" width="609">

Advancement of logical time is now driven by calls to `send-message!`. The messages you send are then handled by the components in their `handle-message` functions, which are functionally pure descriptions of your business logic. The messages encode the change that happens in your application as values. This leads to good design, ease of reasoning, and general peace of mind.


## The data flow model

React components can pass data from parent to children via props. There is no standard way to pass data the other way. To circumvent this shortcoming, you could pass down callback functions that call `setState` on the parent. This callback model is brittle and error-prone.

<img src="https://raw.githubusercontent.com/active-group/reacl/new-composability/doc/react.png" width="160">

In an attempt to fix the React model, Redux and similar frameworks like re-frame and Om have a global application store that you can use to structure your app. With this model, data always flows through a central node.

<img src="https://raw.githubusercontent.com/active-group/reacl/new-composability/doc/redux.png" width="160">

The problem with this model is that components are no longer composable by default. Making components compose is hard work. You have to allocate storage in the global application store manually such that two components of the same kind don't interfere. This is because writing to the global store is essentially a side-effect.

Reacl has a different model of passing data rootwards. Just as with React, data flows leafwards via simple props. In addition, data can flow rootwards via an *application state* that flows out of a component and can be composed on the parent side.

<img src="https://raw.githubusercontent.com/active-group/reacl/new-composability/doc/reacl.png" width="160">

Components are therefore composable by default, because each parent has full control over what it passes down to its children and how it reacts to state changes from below.


## Organization

Reacl consists of two namespaces:

- `reacl2.core` with Reacl's core programming model
- `reacl2.dom` for conveniently constructing virtual DOM nodes in
  ClojureScript

The `reacl2.dom` namespace can be used independently.
While `reacl2.core` depends on `reacl2.dom`, it could also be used
directly with React's virtual-DOM API or other DOM bindings.

In the following, a `:require` clause like this is assumed:

```clj
 (:require [reacl2.core :as reacl :include-macros true]
           [reacl2.dom :as dom :include-macros true])
```

## Basic concepts

Reacl is about programming a web application, which is ultimately
about rendering and updating some DOM elements on a web page and
reacting to user or server input.

At the lowest level, there is the `dom` namespace for creating
(virtual) DOM elements that can be rendered. The following shows
examples of creating simple DOM elements, and abstractions over DOM
elements via plain functions:

```clj
(dom/div "Hello" "World")

(defn bold [text]
  (dom/span {:style {:font-weight "bold"}}
            text))

(defn clock [^js/Date date]
  (bold (.toLocaleTimeString date)))

```

As you can see and might guess, there are functions corresponding to
all the different HTML tags in the `reacl2.dom` namespace, and all
take an optional map of attributes, and none or many additional
arguments for their content, resp. child nodes in the DOM. Strings
represent simple text nodes.

In the following, we build a small application showing the current
time in diferent timezones, introducing the main concepts of Reacl on
the way.

### Reacl components

Besides the primitive virtual DOM elements, there are *Reacl components*
which offer additional features to implement a web application.

Reacl components are usually created from templates called
*classes*. Classes can be defined via the macro `defclass`. We start
by definiting a class for our little application like this:

```clj
(reacl/defclass my-app this state [greeting]
  render
  (dom/div (dom/h1 greeting)
           (clock (:date state))))
```

The arguments to `defclass` are

- `my-app`: a name for the class,
- `this`: a name to reference the component created from the class at runtime,
- `state`: a name for the current *application state* of the component,
- `[greeting]`: an argument vector just like that for functions,
- `render <expr>`: a rendering expression, specifying how a component
  is rendered depending on the current values of the application state and
  arguments.

The name of the class and the name for "this" are required. The
`render` clause is the only required clause, but there are many more
for other purposes.

The name for the current application state is optional. If none is
defined, the components created from the class do not *have* an
application state. You can think of the application state as both an
argument and a return value of the component. If your class only
displays information to the user, then it does not need an application
state and you can pass everything it needs as arguments. But if it
contains controls or mechanisms to modify some piece of data, then
that data should be modeled as the application state of the
component. For the class intended to be used for the toplevel
component, this means its application state should consist of
everything relevant to your application and that might change during
the time it is running.

<!--- remove preceding sentence entirely? --->

It's also good practice to minimize the data passed to a class. Try to
pass as little data as possible in the arguments and the application
state. This practice not only increases its reusability, but also helps to
prevent bugs and to get a good performance of your UI out of the box.

We haven't fully followed that principle in this minimal example
above, as there is not yet a way to modify the date value that is
displayed. We will add that soon, but first, let's look at how to
*run* our application. That's also called *rendering* or *mounting* a
component of that class into the web page. This is done by a call to
`reacl/render-component`, which takes a (real) DOM node from the page,
a class, an initial application state, and any additional arguments
the class needs:

```clj
(def app (reacl/render-component (.getElementById js/document "app")
                                 my-app {:date (js/Date.)} "Hello"))

```

Notice that the passed application state is only an initial value. For
the toplevel component (and only for that!), Reacl automatically
stores and manages updates to that state. We will see how to integrate
the application of other components into the toplevel component later.

### Messages

So the clock currently simply displays the time at which it was
created. To make it tick, we need to update the date value at
runtime. To do so, we will start an interval timer that sends a
*message* to the component, and add a `handle-message` clause to the
class, in which it can update the application state of the component:

```clj
(defrecord Tick [date])

(reacl/defclass my-app this state [greeting]
  render  <same as above>

  handle-message
  (fn [msg]
    (cond
      (instance? Tick msg)
      (reacl/return :app-state (assoc state :date (:date msg))))))

(def app (reacl/render-component <same as above>))

(.setInterval js/window
              #(reacl/send-message! app (->Tick (js/Date.)))
              1000)
```

Any value except `nil` can be sent as a message, but defining a record
type for the message is often convenient. We will integrate starting
and stopping the timer into the class later on, but for now this is
all that's needed to make the clock tick.

### Reusability

We now want to add a control that allows the user to select the
timezone for which to display the current time. For that we first
extend the `clock` function with a timezone parameter:

```clj
(defn clock [^js/Date date timezone]
  (bold (.toLocaleTimeString date "en-US" #js {"timeZone" timezone})))
```

Then we define the following class that has no arguments but defines
a timezone value as the application state of components created from
it:

```clj
(reacl/defclass select-timezone this value []
  render
  (apply dom/select
         {:value value
          :onchange (fn [ev]
                      (reacl/send-message! this (.-value (.-target ev))))}
         (map (fn [[k v]]
                (dom/option {:value k} v))
              {"America/New_York" "New York"
               "Europe/Berlin" "Berlin"
               "Asia/Shanghai" "Shanghai"}))
  handle-message
  (fn [new-value]
    (reacl/return :app-state new-value)))
```

It is rendered as a `select` element, a simple primitive
dropdown. Primitive DOM elements often offer an event handler like
`onchange` here, which is called when the user selects a new item. We
use it to send a message to the component, which then triggers an
update of the application state of the component. The current
application state of the component is named `value` here, and is
passed to the `select` element so that it shows the currently selected
option.

Notice that this component is defined independently of the toplevel
*application state* of our little clock application. In order to use
it in our application, we need to specify where it is rendered, and
how the currently selected timezone is integrated into the application
state of the *parent component* like this:

```clj
(reacl/defclass my-app this state [greeting]
  render
  (dom/div (dom/h1 greeting)
           (select-timezone (reacl/bind this :timezone)) " "
           (clock (:date state) (:timezone state)))

  <rest unchanged>)
```

The `select-timezone` class can be used like a function, where the
first argument must be a *binding* for its application state, followed
by the arguments of the class (none in this case). The binding used
here, `(reacl/bind this :timezone)` specifies that the currently
selected timezone should be stored in the application state of the
`my-app` component (`this`) under the map key `:timezone`.

The second argument to bind can either be a keyword to bind to an
element of an associative collection, like here, an integer to bind to
an element of a sequential collection, or can be a function of two
different arities. Such a function is either called with one argument,
the application state of the parent component, in which case it should
return the current value of the child's application state. Or it is
called with the current application state of the parent and a new
value for the child's application state, in which case it should
return an updated parent application state. A function like this forms
a so called *lens*, and there are libraries with a comprehensive
combinator language for lenses of this kind (e.g. [Active
Clojure](https://github.com/active-group/active-clojure)).

A call to `bind` without a second argument specifies a "1:1" binding
of the parent's and the child's application state. We can use that to
factor out the "clock with timezone selector" component as a class,
and use it twice in our application, each time with a seperate
timezone but with the same date value:

```clj
(reacl/defclass clock-select this timezone [date]
  render
  (dom/div (select-timezone (reacl/bind this))
           (clock date timezone)))

(reacl/defclass my-app this state [greeting]
  render
  (dom/div (dom/h1 greeting)
           (clock-select (reacl/bind this :timezone-1) (:date state))
           (clock-select (reacl/bind this :timezone-2) (:date state)))

  <rest unchanged>)
```

The toplevel `render-component` call has to define a larger initial
applications state now:

```clj
(def app (reacl/render-component (.getElementById js/document "app")
                                 my-app
                                 {:date (js/Date.)
                                  :timezone-1 "Europe/Berlin"
                                  :timezone-2 "America/New_York"}
                                 "Hello"))
```

We've now written a simple reusable class `clock-select` and used it
twice in our application.

### Local state and livecycle

The way we started the interval timer at the beginning of this
introduction is not the way one would normally do something like
that. There is another way that integrates it into the definition of
the `my-app` class. It uses two clauses that are called at specific
points in the *livecycle* of the component: after its first rendering
into the web page, and just before it is removed from it
again. Although the toplevel component of your application might never
be removed, it is important for reusable classes to cleanup things
like a timer, because after a component is removed, no messages must be
sent to it anymore.

To do that properly, we store the id returned by `setInterval` in the
*local state* of the component. Unlike the application state, the
local state is not bound to the state of the parent component and is
fully private to the component. It cannot be accessed or modified from
outside.

The code for `my-app` could look like this:

```clj
(reacl/defclass my-app this state [greeting]
  <rest unchanged>

  local-state [timer-id nil]
  
  component-did-mount
  (fn []
    (reacl/return :local-state
                  (.setInterval js/window
                                #(reacl/send-message! this (->Tick (js/Date.)))
                                1000)))

  component-will-unmount
  (fn []
    (when timer-id
      (.clearInterval js/window timer-id))
    (reacl/return :local-state nil)))
```

The `local-state` clause specifies a name for the local state of the
component, and an initial local state for the components created from
the class. In this case it is the timer id which shall be `nil`
initially. The other two clauses, `component-did-mount` and
`component-will-unmount`, specify the functions to be called at the
aforementioned points in the livecycle of the component. They must
both return a `reacl/return` value, which is used here to update the
value for the timer id.

With these changes, the interval timer is automatically started when
it is needed to keep the clock ticking and stopped afterwards. In the
next section we will also look at how to do this in a pure way, i.e.
without side effects in these two functions.

### Actions

The application state of a component should be used to represent its
steady state that evolves over time, usually via user
interactions. But somtimes a user interaction is only a discrete event
that just 'happens'. These can be modeled as *actions* in Reacl.

As an example, we use a class that renders as a button, and *emits* an
action when it is clicked:

```clj
(reacl/defclass button this [label action]
  render
  (dom/button {:onclick (fn [ev] (reacl/send-message! this :click))}
              label)

  handle-message
  (fn [_]
    (reacl/return :action action)))
```

Actions are emitted by specifying one or more `:action` options to
`reacl/return`. Any value except `nil` can be used as an action. All
actions automatically propagate the component tree upwards, unless
they are captured and handled at some point. To handle actions that
reach the toplevel, you should add `handle-toplevel-action` to the
arguments of `render-component`, like this:

```clj
(defn toplevel-action [app-state action]
  ....
  (reacl/return))

(def app (reacl/render-component (.getElementById js/document "app")
                                 my-app
                                 (reacl/handle-toplevel-action toplevel-action)
                                 {:date (js/Date.)
                                  :timezone-1 "Europe/Berlin"
                                  :timezone-2 "America/New_York"}
                                 "Hello"))
```

Actions that are handled at the toplevel are a good way to isolate
all side effects on the "world" needed by your application, like
starting Ajax requests or storing data into the browser's session
storage. But starting the timer in our main class is such a side
effect, too. We can isolate that in the following way:

```clj
(defrecord StartInterval [ms target id-message tick-message])
(defrecord StopInterval [id])

(defn toplevel-action [app-state action]
  (cond
    (instance? StartInterval action)
    (let [target (:target action)
          ms (:ms action)
          tick-msg (:tick-message action)
          id-msg (:id-message action)
          id (.setInterval js/window
                           #(reacl/send-message! target (tick-msg (js/Date.)))
                           ms)]
      (reacl/return :message [target (id-msg id)]))

    (instance? StopInterval action)
    (let [id (:id action)]
      (.clearInterval js/window (:id action))
      (reacl/return))))
```

The action to start an interval timer takes a reference to the target
component, the time interval, and two pure functions, that create
messages to be sent to the target. The first message is used to send
the timer id to the component, which can then be used to stop the
timer again. The tick message is sent repeatedly every second, just as
before.

Notice the two different ways to send a message: the id is sent to the
target by using the `:message` option of `reacl/return`. During the
evaluation of the toplevel action handler, it is actually not allowed
to call `send-message!`. To react to an action with a message
immediately, always use `reacl/return` in this way. To send messages
from primitive (imperative) event handlers, use `reacl/send-message!`.

With this toplevel action handler, we can now write the timer-related
code in `my-app` with fully pure functions:

```clj
(reacl/defclass my-app this state [greeting]

  local-state [timer-id nil]
  
  component-did-mount
  (fn []
    (reacl/return :action (StartInterval. 1000 this ->TimerId ->Tick)))

  component-will-unmount
  (fn []
    (if timer-id
      (reacl/return :action (StopInterval. timer-id)
                    :local-state nil)
      (reacl/return)))

  handle-message
  (fn [msg]
    (cond
      (instance? TimerId msg) (reacl/return :local-state (:id msg))

      ...))
  
  <rest unchanged>)
```

As mentioned above, actions can also be captured before reaching the
toplevel. The following change to our application does that when
adding a button labeled "Flip". Clicking it shall swap the two
timezones. Using the function `action-to-message` specifies that the
actions emitted by the `button` component should be sent as a message
to `this`, instead of being propagated upwards:

```clj
(defrecord Flip [])

(reacl/defclass my-app this state [greeting]
  render
  (dom/div (dom/h1 greeting)
           (clock-select (reacl/bind this :timezone-1) (:date state))
           (clock-select (reacl/bind this :timezone-2) (:date state))
           (-> (button "Flip" (Flip.))
               (reacl/action-to-message this)))

  handle-message
  (fn [msg]
    (cond
      (instance? Flip msg)
      (reacl/return :app-state (-> state
                                   (assoc :timezone-1 (:timezone-2 state))
                                   (assoc :timezone-2 (:timezone-1 state))))
      ...
      ))
  
  <rest unchanged>)
```

Whether to pass the action value or constructors to a class as an
argument (as in the button example), or to fix the type of values
emitted in the class code (as in the timer example), depends a bit on
the situation and is also a matter of taste. There is also the
function `reacl/map-action`, that allows the parent to transform the
action values flowing out of a child component.

## Summary

In this small introduction, we've learned about the data flow model of
Reacl, the basic namespaces of the library, and we learned how to

- design classes with arguments, application state and actions via
  pure expressions and functions,
- isolate the imperative parts like primitive event handlers from the
  pure parts via `send-message!`,
- break down an application into small reusable classes, resp. combine
  components to form an entire application.

Some of the things not covered are:

- other kinds of bindings like `reacl/bind-local` and
  `reacl/use-reaction` and when to use them,
- further narrowing down a binding with `reacl/focus`,
- how to check invariants and the validity of application state and
  arguments to prevent bugs (the `validate` clause of a class)
- how to unit test your classes, for which Reacl contains
  comprehensive tools,
- how to find bugs and performance issues by tracing what happens at
  runtime (see `reacl2.trace.console`),
- how to access DOM elements from outside of their event handlers (see
  `reacl/refer`),
- when components are re-rendered and how to optimize for
  performance,
- the interoperability with the React framework and other Clojure
  libraries based on React.

You may also want to look at
[reacl-basics](https://github.com/active-group/reacl-basics), a
library providing many reusable components and utilities for most
external effects like timers, browser storage and Ajax calls.

That's it. Hopefully that's enough to get you started.
