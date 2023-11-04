# Presentation Service in Scala

Presentation Service implemented in Scala using various frameworks.

## Play Framework
Scala 3.3 and Play Framework 2.9

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
./sbt "benchmark/Jmh/run -i10 -f1 -t1"
```