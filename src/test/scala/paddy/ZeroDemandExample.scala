package paddy

import akka.stream.ActorMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.{Matchers, FlatSpec}
import akka.actor._
import akka.testkit._
import akka.stream.scaladsl._
import akka.pattern.pipe

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

final class ZeroDemandExample extends FlatSpec with Matchers {

  implicit val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit def ec: ExecutionContext = system.dispatcher

  def actorFlow[A, B](actorRef: ActorRef): Flow[A, B, Unit] = Flow() { implicit b ⇒
    import akka.stream.scaladsl.FlowGraph.Implicits._

    val sink = b.add(Sink.actorRef(actorRef, Finished))
    val publisher = ActorPublisher[B](actorRef)
    val source = b.add(Source(publisher))

    (sink.inlet, source.outlet)
  }

  "An actorFlow" should "expose an ActorPublisher as a source and sink, and propogate demand upstream" in {
    val simplePublisher = system.actorOf(SimplePublisher.props, "simplePublisher")
    val af = actorFlow[SimplePublisher.RequestPublish, SimplePublisher.Publish](simplePublisher)
    val inputFlow = Source(1 to 1000).map(SimplePublisher.RequestPublish).via(af)
    inputFlow.runWith(TestSink.probe[SimplePublisher.Publish]).request(1).expectNext(SimplePublisher.Publish(1)).request(999).expectNextN((2 to 1000).map(SimplePublisher.Publish).toSeq).expectComplete()
    val probe = TestProbe()
    val x = inputFlow.runWith(Sink.fold(List.empty[SimplePublisher.Publish])((acc, p) ⇒ p :: acc)) pipeTo probe.ref
    probe.expectMsg((1000 to 1).map(SimplePublisher.Publish).toList)
  }

}

object SimplePublisher {
  final case class RequestPublish(id: Int)
  final case class Publish(id: Int)

  def props: Props = Props(new SimplePublisher)
}

final class SimplePublisher extends ActorPublisher[SimplePublisher.Publish] with ActorLogging {
  import SimplePublisher._

  private var buffer = Vector.empty[RequestPublish]

  @tailrec private def deliverBuffer(): Unit =
    if (totalDemand > 0 && isActive) {
      /*
       * totalDemand is a Long and could be larger than
       * what buffer.splitAt can accept
       */
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buffer.splitAt(totalDemand.toInt)
        buffer = keep
        use.map(rp ⇒ Publish(rp.id)) foreach onNext
      } else {
        val (use, keep) = buffer.splitAt(Int.MaxValue)
        buffer = keep
        use.map(rp ⇒ Publish(rp.id)) foreach onNext
        deliverBuffer()
      }
    }

  override def receive: Receive = {
    case rp @ RequestPublish(id) ⇒
      log.info(s"totalDemand: $totalDemand")
      log.info(s"isActive: $isActive")

      if (totalDemand > 0 && isActive)
        onNext(Publish(id))
      else {
        buffer :+= rp
        deliverBuffer()
      }

    case Finished =>
      context stop self
  }
}

case object Finished
