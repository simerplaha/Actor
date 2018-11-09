# Actor


The mighty [Akka](https://github.com/akka/akka) is great! 

This is a small type-safe `Actor` class implementation.

# Demo
```scala
libraryDependencies += "com.github.simerplaha" %% "actor" % "0.2.2"
```
Make sure to import `ExecutionContext`
```scala
import scala.concurrent.ExecutionContext.Implicits.global
```

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


## Terminating an Actor on message failure
By default actors are not terminated if there is a failure processing a message. The
following actor enables termination if there is a failure on processing a message.
 
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