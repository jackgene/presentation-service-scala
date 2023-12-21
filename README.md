# Presentation Service in Scala

Presentation Service implemented in Scala 3.3 using various frameworks.

## Play Framework with Akka Actors
This is the reference implementation.

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

## Akka HTTP with Akka Stream
### Running:
```shell
./sbt "project service-akkahttp; clean; run --html-path ../service-play/public/html/deck.html"
```

## Kyo
### Running:
```shell
./sbt "project service-kyotapir; clean; run --html-path service-play/public/html/deck.html"
```

## ZIO
### Running:
```shell
./sbt "project service-ziohttp; clean; run --html-path service-play/public/html/deck.html"
```

## Benchmark
JMH micro benchmarks for some internal data structures

### Running:
```shell
./sbt "benchmark/jmh:run -i10 -f1 -t1"
```