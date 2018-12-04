# Actor

**[`Actor`](https://github.com/simerplaha/Actor#actor-1)** - A small type-safe class that implements most commonly used Actor APIs
including ask (`?`) which returns a typed `Future[R]`.

**[`WiredActor`](https://github.com/simerplaha/Actor#wiredactor)** - Invoke functions directly on `Actor`s.
Send functions as messages. 

# Setup
```scala
libraryDependencies += "com.github.simerplaha" %% "actor" % "0.3"
```
Make sure to import `ExecutionContext`
```scala
import scala.concurrent.ExecutionContext.Implicits.global
```

# WiredActor
Managing message types for each `Actor` can be a lot of work for `Actor`s that do 
not require message serialisation. 

`WiredActor`s can be created on any `class`  instance or `object` where functions 
can be directly invoked on a class instance.

Functions can also be scheduled similar to message scheduling 
in `Actor`s. See following example code.

## Create a `WiredActor`
```scala
//your class that contains Actor functions
object MyImpl {
  def hello(name: String): String =
    s"Hello $name"

  def helloFuture(name: String): Future[String] =
    Future(s"Hello $name") //some future operation
}

//create WiredActor
val actor = Actor.wire(MyImpl)
```

## ask - Invoke Actor function 
```scala
//invoke function
val response: Future[String] = actor.ask(_.hello("World"))
response.foreach(println)
```

## askFlatMap

```scala
val responseFlatMap: Future[String] = actor.askFlatMap(_.helloFuture("World from Future"))
responseFlatMap.foreach(println)
```

## send

```scala
val unitResponse: Unit = actor.send(_.hello("World again!"))
```

## schedule

```scala
//schedule a function call on the actor. Returns Future response and TimerTask to cancel.
val scheduleResponse: (Future[String], TimerTask) = actor.scheduleAsk(delay = 1.second)(_.hello("World!!"))
scheduleResponse._1.foreach(println)
```

## PingPong example using `WiredActor`
```scala
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Run extends App {

  class WiredPingPong(var pingCount: Int, var pongCount: Int) {
    def ping(replyTo: WiredActor[WiredPingPong]): Unit = {
      pingCount += 1
      println(s"pingCount: $pingCount")
      replyTo.send(_.pong(replyTo))
    }

    def pong(replyTo: WiredActor[WiredPingPong]): Unit = {
      pongCount += 1
      println(s"pongCount: $pongCount")
      replyTo.send(_.ping(replyTo))
    }
  }

  Actor
    .wire(new WiredPingPong(0, 0))
    .send {
      (impl, self) =>
        impl.ping(self)
    }

  Thread.sleep(1.seconds.toMillis)
}
```


# Actor

## Stateless Actor

```scala
val actor =
  Actor[Int](
    (message, self) =>
      println(message)
  )

actor ! 1
```

## Stateful Actor
```scala
case class State(var counter: Int)

val actor =
  Actor[Int, State](State(0))(
    (message, self) =>
      self.state.counter += 1
  )
```

## Timer actor
A timer actor will process messages in batches after the set delays. Similar to above a stateful timer Actor
can also be created.

```scala
import scala.concurrent.duration._

//stateless timer actor
val actor =
  Actor.timer[Int](delays = 1.second) {
    (message, self) =>
    //do something
  }
```

## Scheduling messages to self
`self.schedule` returns a java `TimerTask` which is cancellable.

```scala
val actor =
  Actor[Int](
    (message, self) =>
      self.schedule(message = 1, delay = 1.second)  
  )
```


## Terminating an Actor

```scala
val actor =
  Actor[Int](
    (message, self) =>
      println(message)
  )

actor.terminate()
//cannot send messages to a terminated actor.
(actor ! 1) shouldBe Left(Result.TerminatedActor)
```

## Ask - Get a Future response
```scala
case class CreateUser(name: String)(val replyTo: ActorRef[Boolean])

val actor = Actor[CreateUser] {
  (message: CreateUser, _) =>
    message.replyTo ! true
}

val response: Future[Boolean] = (actor ? CreateUser("Tony Stark")).right.get

Await.result(response, 1.second)
```

## Terminating an Actor on message failure
By default actors are not terminated if there is a failure processing a message. The
following actor enables termination if there is a failure processing a message.
 
```scala
val actor =
  Actor[Int](
    (message, self) =>
      throw new Exception("Kaboom!")
  ).terminateOnException() //enable terminate on exception

(actor ! 1) shouldBe Right(Result.Sent) //first message sent is successful
eventually(actor.isTerminated() shouldBe true) //actor is terminated
(actor ! 2) shouldBe Left(Result.TerminatedActor) //cannot sent messages to a terminated actor
```

## Testing
Borrowing ideas from Akka the `TestActor` implements APIs to test messages in an Actor's mailbox.

```scala
val actor = TestActor[Int]()

actor.expectNoMessage(after = 1.second) //expect a message after delay in the Actor's mailbox
val got = actor.getMessage() //fetch the first message in the actor's mailbox
actor.expectMessage[Int]() //expect a message of some type
```

## PingPong example

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.github.simerplaha.actor._

case class Pong(replyTo: ActorRef[Ping])
case class Ping(replyTo: ActorRef[Pong])
case class State(var count: Int)

val ping =
  Actor[Ping, State](State(0)) {
    case (message, self) =>
      self.state.count += 1
      println(s"Ping: ${self.state.count}")
      message.replyTo ! Pong(self)
  }

val pong =
  Actor[Pong, State](State(0)) {
    case (message, self) =>
      self.state.count += 1
      println(s"Pong: ${self.state.count}")
      message.replyTo ! Ping(self)
  }

pong ! Pong(ping)

//run this for 1 seconds
Thread.sleep(1.second.toMillis)
```