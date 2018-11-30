# Actor


The mighty [Akka](https://github.com/akka/akka) is great! 

This is a small type-safe `Actor` class that implements most commonly used Actor APIs
including ask (`?`) which returns a typed `Future[R]`.

Create `WiredActor` where `Actor`s can be created from any `class` or `object` instance.
`WiredActor` allow for direct invocation of functions without needing 
to create custom messages.

# Setup
```scala
libraryDependencies += "com.github.simerplaha" %% "actor" % "0.2.3"
```
Make sure to import `ExecutionContext`
```scala
import scala.concurrent.ExecutionContext.Implicits.global
```

# Wired Actor
Managing `sealed` message classes for each `Actor` can be a bit of work so instead `WiredActor`
can be created where functions are invoked directly on an `Actor`. 

`WiredActor`s can be created on any `class` instance or `object`.

Functions can also be scheduled. See following example code.

```scala

object WiredDemo extends App {
  //suppose this is your implementation
  object MyImpl {
    //pure function
    def hello(name: String): String =
      s"Hello $name"

    def helloFuture(name: String): Future[String] =
      Future(s"Hello $name") //some delay operation
  }
  //create a wired Actor from your implementation
  val actor = Actor.wire(MyImpl)

  //call functions on the Actor.
  val response: Future[String] = actor.call(_.hello("World"))
  response.foreach(println)

  //call functions on the Actor.
  val responseFlatMap: Future[String] = actor.callFlatMap(_.helloFuture("World from Future"))
  responseFlatMap.foreach(println)

  //send is fire and forget. Returns type Unit
  val responseUnit: Unit = actor.send(impl => println(impl.hello("World again!")))

  //schedule a function call on the actor. Returns Future response and TimerTask to cancel.
  val scheduleResponse: (Future[String], TimerTask) = actor.scheduleCall(delay = 1.second)(_.hello("World!!"))
  scheduleResponse._1.foreach(println)

  //Give enough time for this test to run
  Thread.sleep(2000)
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