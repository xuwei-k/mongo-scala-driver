/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.scala.internal

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

import com.mongodb.MongoException
import com.mongodb.async.client.{ Observable => JObservable, Observer => JObserver, Subscription => JSubscription }

import org.mongodb.scala._
import org.scalatest.{ FlatSpec, Matchers }

class ScalaObservableSpec extends FlatSpec with Matchers {

  "ScalaObservable" should "allow for inline subscription" in {
    var results = ArrayBuffer[Int]()
    observable().subscribe((res: Int) => results += res)
    results should equal(1 to 100)

    var thrown = false
    observable(fail = true).subscribe((res: Int) => (), (t: Throwable) => thrown = true)
    thrown should equal(true)

    var completed = false
    observable().subscribe((res: Int) => (), (t: Throwable) => (), () => completed = true)
    completed should equal(true)
  }

  it should "have a foreach method" in {
    var results = ArrayBuffer[Int]()
    observable().foreach((res: Int) => results += res)
    results should equal(1 to 100)
  }

  it should "have a transform method" in {
    var completed = false
    var results = ArrayBuffer[String]()
    observable[Int]()
      .transform((res: Int) => res.toString, (ex: Throwable) => ex)
      .subscribe((s: String) => results += s, (t: Throwable) => (), () => completed = true)
    results should equal((1 to 100).map(_.toString))
    completed should equal(true)

    completed = false
    val exception = new MongoException("New Exception")
    var throwable: Option[Throwable] = None
    observable[Int](fail = true)
      .transform((res: Int) => res, (ex: Throwable) => exception)
      .subscribe((s: Int) => (), (t: Throwable) => throwable = Some(t), () => completed = true)

    completed should equal(false)
    throwable.get should equal(exception)
  }

  it should "have a map method" in {
    var results = ArrayBuffer[String]()
    var completed = false
    observable[Int]()
      .map((res: Int) => res.toString)
      .subscribe((s: String) => results += s, (t: Throwable) => (), () => completed = true)
    results should equal((1 to 100).map(_.toString))
    completed should equal(true)
  }

  it should "have a flatMap method" in {
    def myObservable(fail: Boolean = false): Observable[String] =
      observable[Int](fail = fail).flatMap((res: Int) => observable(List(res.toString)))

    var results = ArrayBuffer[String]()
    myObservable().subscribe((s: String) => results += s)
    results should equal((1 to 100).map(_.toString))

    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((s: String) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    var completed = false
    myObservable().subscribe((s: String) => (), (t: Throwable) => t, () => completed = true)
    completed should equal(true)
  }

  it should "have a filter method" in {
    def myObservable(fail: Boolean = false): Observable[Int] = observable[Int](fail = fail).filter((i: Int) => i % 2 != 0)

    var results = ArrayBuffer[Int]()
    myObservable().subscribe((i: Int) => results += i)
    results should equal((1 to 100).filter(i => i % 2 != 0))

    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((s: Int) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    var completed = false
    myObservable().subscribe((s: Int) => (), (t: Throwable) => t, () => completed = true)
    completed should equal(true)
  }

  it should "have a withFilter method" in {
    def myObservable(fail: Boolean = false): Observable[Int] = observable[Int](fail = fail).withFilter((i: Int) => i % 2 != 0)

    var results = ArrayBuffer[Int]()
    myObservable().subscribe((i: Int) => results += i)
    results should equal((1 to 100).filter(i => i % 2 != 0))

    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((s: Int) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    var completed = false
    myObservable().subscribe((s: Int) => (), (t: Throwable) => t, () => completed = true)
    completed should equal(true)
  }

  it should "have a collect method" in {
    def myObservable(fail: Boolean = false): Observable[Seq[Int]] = {
      observable[Int](fail = fail).collect()
    }

    var results = ArrayBuffer[Int]()
    myObservable().subscribe((i: Seq[Int]) => results ++= i)
    results should equal(1 to 100)

    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((s: Seq[Int]) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    var completed = false
    myObservable().subscribe((s: Seq[Int]) => (), (t: Throwable) => t, () => completed = true)
    completed should equal(true)
  }

  it should "have a foldLeft method" in {
    def myObservable(fail: Boolean = false): Observable[Int] = {
      observable[Int](fail = fail).foldLeft(0)((l: Int, i) => l + 1)
    }

    var results = 0
    myObservable().subscribe((i: Int) => results = i)
    results should equal(100)

    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((s: Int) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    var completed = false
    myObservable().subscribe((s: Int) => (), (t: Throwable) => t, () => completed = true)
    completed should equal(true)
  }

  it should "have a recover method" in {
    var results = ArrayBuffer[Int]()
    observable().recover({ case e: ArithmeticException => 999 }).subscribe((i: Int) => results += i)
    results should equal(1 to 100)

    var errorSeen: Option[Throwable] = None
    observable[Int](fail = true)
      .recover({ case e: ArithmeticException => 999 })
      .subscribe((s: Int) => (), (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]

    results = ArrayBuffer[Int]()
    observable(fail = true)
      .transform(i => i, (t: Throwable) => new ArithmeticException())
      .recover({ case e: ArithmeticException => 999 })
      .subscribe((i: Int) => results += i)
    results should equal((1 to 50) :+ 999)
  }

  it should "have a recoverWith method" in {
    var results = ArrayBuffer[Int]()
    var completed = false
    observable()
      .recoverWith({ case e: ArithmeticException => observable(1000 to 1001) })
      .subscribe((i: Int) => results += i, (t: Throwable) => (), () => completed = true)
    results should equal(1 to 100)
    completed should equal(true)

    results = ArrayBuffer[Int]()
    var errorSeen: Option[Throwable] = None
    completed = false
    observable[Int](fail = true)
      .recoverWith({ case e: ArithmeticException => observable[Int](1000 to 1001) })
      .subscribe((i: Int) => results += i, (fail: Throwable) => errorSeen = Some(fail), () => completed = true)
    errorSeen.getOrElse(None) shouldBe a[Throwable]
    results should equal(1 to 50)
    completed should equal(false)

    results = ArrayBuffer[Int]()
    observable(fail = true)
      .transform(i => i, (t: Throwable) => new ArithmeticException())
      .recoverWith({ case e: ArithmeticException => observable(1000 to 1001) })
      .subscribe((i: Int) => results += i)
    results should equal((1 to 50) ++ (1000 to 1001))

    results = ArrayBuffer[Int]()
    observable(fail = true)
      .transform(i => i, (t: Throwable) => new ArithmeticException())
      .collect()
      .recoverWith({ case e: ArithmeticException => observable(1000 to 1001).collect() })
      .subscribe((i: Seq[Int]) => results ++= i)
    results should equal((1000 to 1001))
  }

  it should "have a zip method" in {
    var results = ArrayBuffer[(Int, String)]()
    observable[Int]().zip(observable().map(i => i.toString)).subscribe((res: (Int, String)) => results += res)
    results should equal((1 to 100).zip((1 to 100).map(_.toString)))
  }

  it should "have a fallBackTo method" in {
    var results = ArrayBuffer[Int]()
    observable().fallbackTo(observable[Int](1000 to 1001)).subscribe((i: Int) => results += i)
    results should equal(1 to 100)

    results = ArrayBuffer[Int]()
    observable(fail = true)
      .fallbackTo(observable[Int](1000 to 1001))
      .subscribe((i: Int) => results += i)
    results should equal((1 to 50) ++ (1000 to 1001))

    var errorMessage = ""
    TestObservable[Int](1 to 100, 10, "Original Error")
      .fallbackTo(TestObservable[Int](1000 to 1001, 1000, "Fallback Error"))
      .subscribe((i: Int) => i, (t: Throwable) => errorMessage = t.getMessage)
    errorMessage should equal("Original Error")
  }

  it should "have an andThen method" in {
    var results = ArrayBuffer[Int]()
    def myObservable(fail: Boolean = false): Observable[Int] = {
      observable[Int](1 to 100, fail = fail) andThen {
        case Success(r)  => results += 999
        case Failure(ex) => results += -999
      }
    }

    myObservable().subscribe((i: Int) => results += i)
    results should equal((1 to 100) :+ 999)

    results = ArrayBuffer[Int]()
    var errorSeen: Option[Throwable] = None
    myObservable(true).subscribe((i: Int) => results += i, (fail: Throwable) => errorSeen = Some(fail))
    errorSeen.getOrElse(None) shouldBe a[Throwable]
    results should equal((1 to 50) :+ -999)

    results = ArrayBuffer[Int]()
    var completed = false
    myObservable().subscribe((i: Int) => results += i, (t: Throwable) => t, () => completed = true)
    results should equal((1 to 100) :+ 999)
    completed should equal(true)
  }

  it should "work with for comprehensions" in {
    def f = observable(1 to 5)
    def g = observable(100 to 101)
    val h = for {
      x: Int <- f // returns Observable(1 to 5)
      y: Int <- g // returns Observable(100 to 100)
    } yield x + y
    val expectedResults = (1 to 5).flatMap(i => (100 to 101).map(x => x + i))

    var results = ArrayBuffer[Int]()
    var completed = false
    h.subscribe((s: Int) => results += s, (t: Throwable) => t, () => completed = true)
    results should equal(expectedResults)
    completed should equal(true)

    results = ArrayBuffer[Int]()
    completed = false
    val fh: Observable[Int] = f flatMap { (x: Int) => g map { (y: Int) => x + y } }
    fh.subscribe((s: Int) => results += s, (t: Throwable) => t, () => completed = true)
    results should equal(expectedResults)
    completed should equal(true)
  }

  it should "work with andThen as expected" in {
    var results = ArrayBuffer[Int]()
    var completed = false
    observable() andThen {
      case r => throw new MongoException("Exception")
    } andThen {
      case Success(_) => results += 999
      case Failure(t) => results += -999
    } subscribe ((s: Int) => results += s, (t: Throwable) => t, () => completed = true)

    results should equal((1 to 100) :+ 999)
    completed should equal(true)

    results = ArrayBuffer[Int]()
    completed = false
    observable(fail = true) andThen {
      case r => throw new MongoException("Exception")
    } andThen {
      case Success(_) => results += 999
      case Failure(t) => results += -999
    } subscribe ((s: Int) => results += s, (t: Throwable) => t, () => completed = true)

    results should equal((1 to 50) :+ -999)
    completed should equal(false)
  }

  it should "convert to a Future" in {
    var results = ArrayBuffer[Int]()
    var errorSeen: Option[Throwable] = None
    val happyFuture = observable[Int]().toFuture()
    var latch = new CountDownLatch(1)

    happyFuture.onComplete({
      case Success(res) =>
        results ++= res
        latch.countDown()
      case Failure(throwable) => errorSeen = Some(throwable)
    })
    latch.await(10, TimeUnit.SECONDS)
    results should equal(1 to 100)
    errorSeen.isEmpty should equal(true)

    results = ArrayBuffer[Int]()
    latch = new CountDownLatch(1)
    val unhappyFuture = observable[Int](fail = true).toFuture()
    unhappyFuture.onComplete({
      case Success(res) => results ++= res
      case Failure(throwable) =>
        errorSeen = Some(throwable)
        latch.countDown()
    })
    intercept[MongoException] {
      Await.result(unhappyFuture, Duration(10, TimeUnit.SECONDS))
    }
    latch.await(10, TimeUnit.SECONDS)
    results should equal(List())
    errorSeen.nonEmpty should equal(true)
    errorSeen.getOrElse(None) shouldBe a[Throwable]
  }

  it should "provide a head method" in {
    Await.result(observable[Int]().head(), Duration(10, TimeUnit.SECONDS)) should equal(1)
    Await.result(observable[Int](fail = true).head(), Duration(10, TimeUnit.SECONDS)) should equal(1)

    intercept[MongoException] {
      Await.result(TestObservable[Int](Observable[Int](1 to 10), failOn = 1).head(), Duration(10, TimeUnit.SECONDS))
    }

    intercept[IllegalStateException] {
      Await.result(TestObservable[Int](Observable(List[Int]())).head(), Duration(10, TimeUnit.SECONDS))
    }
  }

  it should "work with Java Observer" in {
    var results = ArrayBuffer[Int]()
    var errorSeen: Option[Throwable] = None
    var latch = new CountDownLatch(1)

    var subscription: Option[JSubscription] = None
    val observer = new JObserver[Int]() {
      override def onError(e: Throwable): Unit = {
        errorSeen = Some(e)
        latch.countDown()
      }

      override def onSubscribe(sub: JSubscription): Unit = {
        subscription = Some(sub)
        sub.request(Long.MaxValue)
      }

      override def onComplete(): Unit = latch.countDown()

      override def onNext(result: Int): Unit = results += result
    }

    observable[Int]().subscribe(observer)
    latch.await(10, TimeUnit.SECONDS)
    results should equal(1 to 100)

    subscription = None
    results = ArrayBuffer[Int]()
    errorSeen = None
    latch = new CountDownLatch(1)

    observable(fail = true).subscribe(observer)
    latch.await(10, TimeUnit.SECONDS)
    results should equal(1 to 50)
    errorSeen.nonEmpty should equal(true)
    errorSeen.getOrElse(None) shouldBe a[Throwable]
  }

  def observable[A](from: Iterable[A] = (1 to 100).toIterable, fail: Boolean = false): Observable[A] = {
    fail match {
      case true  => TestObservable[A](Observable(from), failOn = 51)
      case false => TestObservable[A](Observable(from))
    }
  }

  "Observers" should "support JSubscriptions" in {
    val observer = new Observer[Int]() {
      override def onError(e: Throwable): Unit = {}

      override def onSubscribe(subscription: Subscription): Unit = {
        subscription.request(1)
        subscription.unsubscribe()
        subscription.isUnsubscribed should equal(true)
      }

      override def onComplete(): Unit = {}

      override def onNext(result: Int): Unit = {}
    }

    var requested = 0
    val subscription = new JSubscription {
      var subscribed = true

      override def isUnsubscribed: Boolean = !subscribed

      override def request(n: Long): Unit = requested += 1

      override def unsubscribe(): Unit = subscribed = false
    }

    observer.onSubscribe(subscription)
    subscription.isUnsubscribed should equal(true)
    requested should equal(1)
  }

  "Observers" should "automatically subscribe and request Long.MaxValue" in {
    val observer = new Observer[Int]() {
      override def onError(e: Throwable): Unit = {}

      override def onComplete(): Unit = {}

      override def onNext(result: Int): Unit = {}
    }

    var requested: Long = 0
    val subscription = new JSubscription {

      override def isUnsubscribed: Boolean = false

      override def request(n: Long): Unit = requested = n

      override def unsubscribe(): Unit = {}
    }

    observer.onSubscribe(subscription)
    requested should equal(Long.MaxValue)
  }

}
