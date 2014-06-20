# Rowboat

Rowboat is work being done on the next generation of Trireme.

## Goals

Rowboat is being built solely for Java 8 and the "Nashorn" JavaScript engine. It will not run on any
earlier version of Java and won't work there.

Other than that, rowboat has essentially the same goals as Trireme:

* Embed many Node.js applications inside a single JVM
* Isolate each one's access to the network and filesystem
* Be as compatible as possible with regular Node.js
* Be frugal with dependencies so that it may be embedded in the maximum number of places.

## Status

A bunch of key stuff is limping along, well enough to run basic benchmarks. HTTP is not there yet which is one
big thing missing for most use cases obviously.

Modules that are working, although only passing a subset of tests, include:

* buffer
* net
* child_process
* fs
* timers
* dns (lookups only like Trireme, at least for now)
* JavaScript-only modules like util, url, punycode, etc. etc. etc.

Big stuff that is not working includes:

* http
* crypto
* zlib

Most of the work is consisting of copying code from Trireme, which I'm doing because Nashorn and the way that we're
using it is very different, in that we're largely calling Java code directly from JavaScript and vice versa,
as opposed to the more complex integration pattern that Trireme uses. Microbenchmarks (run using Caliper)
showed me that this style of integrating Java and JavaScript in Nashorn is much faster than the alternatives.

The result is much simpler code, and I am quite happy about that.

## Performance

There are two reasons to use Nashorn:

* There is a team working on it to ensure that it stays up to date with the JavaScript language
* It is supposed to be faster.

Right now, the "v8" benchmark suite, as copied into the regular Node.js distribution, will run on both Rowboat
and Trireme. Trireme is a bit faster for some benchmarks, Nashorn is a bit faster for others, and Nashorn is
much faster for a few.

On the other hand, Node.js benchmarks that run today are all much much slower than the same benchmarks
on Trireme. Some of
this may be due to the baroque nature of the Buffer implementation, but there is something else weird
going on.


