# Why JSX feels weird

This writing started with a question about why JSX feels the way it does--
weird.  So this is a space to get context on the problem space.

## WebApps and HTML

This is obvious, but webapps are just things that produce HTML.

- command line programs produce __text__
- game engines produce __bitmaps__
- desktop apps produce __windows__
- daemons produce nothing

Clearly, this is oversimplified.  But HTML is primary output of a webapp.

### Rendering HTML

Webapps do not just produce Static HTML.  Content is often dynamic and it helps
to have a model for generating the right HTML for different situations.

```
data -----> HTML w/ eval syntax -------------------> literal HTML    e.g. erb, mustache, php
            (joined)

data -----> literal HTML -> queries/transforms ----> literal HTML    e.g. enlive
            (split)

data -----> general purpose language --------------> literal HTML    e.g. React
            (joined)  ^
                      |
                      |
                      |
                      |
                      | Focus of the talk
```

### Representing HTML

HTML is a tree of a data.  There is no literal way to represent HTML in a
general purpose language like JS (almost with [e4x] though), we have the
following:

```
 <ul   props>  children </ul>

  ul( {props}, children )      --->  function call producing an object

["ul" {props}, children ]      --->  plain data (deferred)
```

[e4x]:https://en.wikipedia.org/wiki/ECMAScript_for_XML
[JSONML]:http://www.jsonml.org/

More concretely:

```html
<ul class="foo">
  <li>hello</li>
  <li>world</li>
</ul>
```

```js
ul({class:"foo"},
  li("hello"),
  li("world")
);
```

```coffee
ul {class:"foo"}
  li "hello"
  li "world"
```

```js
["ul", {class:"foo"},
  ["li", "hello"],
  ["li", "world"]
]
```

```js
["ul.foo"
  ["li", "hello"],
  ["li", "world"]
]
```

```clj
[:ul.foo
  [:li "hello"]
  [:li "world"]]

;; the selling point of clojure here
;; is that it encourages you solve most problems
;; using plain, immutable data.
```

(Syntactic hints are important when choosing from the above.)

All methods are consistent and composable.  This is how you build HTML, the syntax
doesn't matter.

### What Facebook Chose

Facebook went with the function calls, and also under an optional JSX,
which activates at `<tag>` and deactivates at last `</tag>`, allowing
`{}` to insert normal JS, even more JSX.

```html
<ul class="foo">
  <li>hello</li>
  <li>world</li>
</ul>
```

```js
ul({class:"foo"},
  li("hello"),
  li("world")
);
```

## Using `if` inside HTML

```mustache
<!-- mustache -->
<div>
  {{#if condition}}
    <span>hello</span>
  {{/if}}
</div>
```

```coffee
# coffee
div
  if condition
    span "hello"
```

```js
// js (error)
div(
  if(condition) {
    span("hello");
  })
```

```js
// js
div(
  condition ? span("hello") : null
  )
```

```js
// js
div(
  (() => {
    if (condition) {
      return span("hello");
    }
  })()
);
```

```jsx
// jsx
<div>
  {(() => {
    if (condition) {
      return <span>hello</span>;
    }
  })()}
</div>
```

```js
// js (recommended)
if (condition) {
  msg = span("hello");
}
div(
  msg
);
```

```jsx
// jsx
if (condition) {
  msg = <span>hello</span>;
}
<div>
  {msg}
</div>
```

## Using `for` inside HTML

```mustache
<ul>
  {{#for m in messages}}
    <li>m</li>
  {{/for}}
</ul>
```

```coffee
# coffee
ul
  for m in messages
    li m
```

```js
// js (error)
ul(
  for (m of messages) {
    span("hello");
  })
```

```js
// js (recommend)
ul(
  messages.map(m => {
    return li(m);
  })
);
```

```jsx
// jsx
<ul>
  {messages.map(m => {
    return <li>{m}</li>;
  })}
</ul>
```

```js
// js (alternate)
var items = messages.map(m => {
              return li(m);
            });
ul(
  items
);
```

```jsx
// js (alternate)
var items = messages.map(m => {
              return <li>{m}</li>;
            });
<ul>
  {items}
</ul>
```

## The problem with JS and JSX

- `if` and `for` are statements, not expressions
  - no expressions, no expressivity (huge oversight that coffeescript fixes)
  - JSX was the big wakeup call for this shortcoming in JS

- JSX pros:
  - syntactic hint - immediately recognize what is a component

- JSX cons:
  - extra syntax

## Conclusions

No short-term solution to the expressivity problem in JS.
Using other languages can help ameliorate the problem, but abstracting
areas that require control flow (e.g. `if`, `for`) into variables
is fine for now.

I'm not sure this space is fully explored yet, but it's fun to sort
of see how different communities solve this problem.

---

## Appendix

### ClojureScript

Did you noticed how closely a function call looks like plain data?

```
  ul( {props}, children )      --->  typed object (immediately evaluated)

["ul" {props}, children ]      --->  plain data (deferred)
```

Something to chew on.  They're both very similar in that one is evaluated
(transformed into something to immediately produce a new value)
and the other is literal data.

...todo...

And if you think the CoffeeScript version was very succinct, but too
implicit about what was actually happening, I think Clojure occupies
a nice sweet spot between indentation-based editing, 
if you use something like [Parinfer].

