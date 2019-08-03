package benchmark

import java.util.concurrent.TimeUnit

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Semaphore => CatsSemaphore}
import cats.implicits._
import cats.{Parallel, Traverse}
import monix.catnap.{ConcurrentQueue, Semaphore}
import monix.eval.Task
import monix.reactive.Observable
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * jmh:run -i 10 -wi 8 -f 1 -t 1 benchmark.GatherNBenchmark
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class GatherNBenchmark {

  val count = 10000
  val parallelism = 10

  @Benchmark
  def manualImplementationWanderNBenchmark(): List[Int] = {
    val tasks = (0 until count).toList
    val f = Task.wanderN(parallelism)(tasks)(_ => Task.eval(1)).runToFuture
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def observableMapParallelOrderedBenchmark(): List[Int] = {
    val tasks = (0 until count).toList
    val f = Observable
      .fromIterable(tasks)
      .mapParallelOrdered(parallelism)(_ => Task.eval(1))
      .toListL
      .runToFuture
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def observableMapParallelUnorderedBenchmark(): List[Int] = {
    val tasks = (0 until count).toList
    val f = Observable
      .fromIterable(tasks)
      .mapParallelUnordered(parallelism)(_ => Task.eval(1))
      .toListL
      .runToFuture
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def wanderNMonixSemaphoreBenchmark(): List[Int] = {
    val tasks = (0 until count).toList
    val f = wanderNSemaphore(parallelism)(tasks)(_ => Task.eval(1)).runToFuture
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def wanderNConcurrentQueueBenchmark(): Seq[Int] = {
    val tasks = (0 until count).toList
    val f = wanderNQueue(parallelism)(tasks)(_ => Task.eval(1)).runToFuture
    Await.result(f, Duration.Inf)
  }

  @Benchmark
  def catsEffectParTraverseNBenchmark(): Seq[Int] = {
    val tasks = (0 until count).toList
    val f = parTraverseN(parallelism)(tasks)(_ => Task.eval(1)).runToFuture
    Await.result(f, Duration.Inf)
  }

  private def wanderNSemaphore[T, U](
    parallelism: Int
  )(items: Seq[T])(f: => T => Task[U]): Task[List[U]] =
    for {
      semaphore <- Semaphore[Task](provisioned = parallelism)
      tasks = for (n <- items.indices) yield {
        semaphore.withPermit(f(items(n)))
      }
      res <- Task.gatherUnordered(tasks)
    } yield res

  private def wanderNQueue[T, U](
    parallelism: Int
  )(items: Seq[T])(f: => T => Task[U]): Task[Seq[U]] = {
    for {
      queue <- ConcurrentQueue.bounded[Task, (Deferred[Task, U], T)](
        items.length
      )
      pairs <- Task.traverse(items)(
        item => Deferred[Task, U].map(p => (p, item))
      )
      _ <- queue.offerMany(pairs)
      _ <- Task.gather(List.fill(parallelism) {
        queue.poll
          .flatMap {
            case (p, a) =>
              f(a).flatMap(p.complete)
          }
          .loopForever
          .start
      })
      res <- Task.sequence(pairs.map(_._1.get))
    } yield res
  }

  private def parTraverseN[T[_]: Traverse, M[_], F[_], A, B](n: Long)(
    ta: T[A]
  )(f: A => M[B])(implicit M: Concurrent[M], P: Parallel[M, F]): M[T[B]] =
    for {
      semaphore <- CatsSemaphore(n)(M)
      tb <- ta.parTraverse { a =>
        semaphore.withPermit(f(a))
      }
    } yield tb
}
