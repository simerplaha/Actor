# Actor

A type-safe Actor class.

The mighty Akka is great but sometimes I just need a small Actor implementation 
that would just process messages sequentially or in batches with a timed delay.

# Demo
import `ExecutionContext`

```scala
import scala.concurrent.ExecutionContext.Implicits.global 
```
## Stateless Actor

```scala
val actor =
  Actor[Int](
    (message: Int, self: ActorRef[Int]) =>
      println(message)
  )

actor ! 1
```

## Stateful Actor
```scala
case class MyCounter(var counter: Int)

val actor =
  Actor[Int, MyCounter](MyCounter(0))(
    (message, self) =>
      self.state.counter += 1
  )
```

## Timer actor
A timer actor will process messages in batches after the provided delay.
```scala
import scala.concurrent.duration._

val actor =
  Actor.timer[Int](delays = 1.second) {
    (message, self) =>
    //do something
  }
```

## Scheduling messages to self
```scala
val actor =
  Actor[Int](
    (message, self) =>
      self.schedule(message = 1, delay = 1.second) //sends this message to itself after 1 second.
  )
```

## Testing
Borrowing ideas from Akka the `TestActor` implements APIs to test messages in an Actor's mailbox.

```scala
val actor = TestActor[Int]()

actor.expectNoMessage(after = 1.second) //expect a message after delay in the Actor's mailbox
val got = actor.getMessage() //fetch the first message in the actor's mailbox
actor.expectMessage[Int] //expect a message of some type
```

