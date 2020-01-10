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
               [reacl "2.1.3"]]
```

## Documentation

API documentation for the latest release: [CljDoc](https://cljdoc.xyz/d/reacl/reacl/CURRENT).

An introduction to the library can be found [here](doc/intro.md)

And the sources on [Github](http://active-group.github.io/reacl/).

## Running the tests

The following commands run the tests defined in `test-dom` and `test-nodom`,
respectively. To execute them, [karma](https://github.com/karma-runner/karma) is needed.

```
lein doo chrome-headless test-dom
lein doo chrome-headless test-nodom
```

You may substitute `chrome-headless` with a runner of your choice.

## License

Copyright © 2015-2020 Active Group GmbH

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
