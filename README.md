# Presentation Service in Scala

Presentation Service implemented in Scala using various frameworks.

## Play Framework
Scala 2.13 and Play Framework 2.9

### Running:
Build the deployment artifact:
```shell
./sbt "project service-play; clean; stage"
```

Run:
```shell
./service-play/target/universal/stage/bin/presentation-service-play
```

### Developing
Run with live updates:
```shell
./sbt service-play/run
```

## Benchmark
JMH micro benchmarks for some internal data structures

### Running:
```shell
./sbt "benchmark/jmh:run -i10 -f1 -t1"
```

## What Does it Mean for a Language to be Strongly Typed?
Strong Typing is not the same as Static Typing.

In this talk, we’ll describe what it means for a programming language to be strongly typed.

We will spend some time talking about mechanics of how strongly typed languages prevent errors in your program before it even runs, keeping your software reliable.

Finally, we provide you with some techniques to producing reliable software when using weakly typed languages.
