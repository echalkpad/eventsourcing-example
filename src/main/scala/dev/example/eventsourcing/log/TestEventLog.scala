package dev.example.eventsourcing.log

import java.util.concurrent.CopyOnWriteArrayList

import dev.example.eventsourcing.domain.Event

class TestEventLog[E <: Event] extends EventLog[E] with Iterable[E] {
  val storedEvents = new CopyOnWriteArrayList[E]()

  import scala.collection.JavaConverters._

  def append(event: E) = storedEvents.add(event)
  def iterator = storedEvents.asScala.toList.iterator
}

object TestEventLog {
  def apply[E <: Event]() = new TestEventLog[E]
}
