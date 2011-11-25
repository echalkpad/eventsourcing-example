package dev.example.eventsourcing.service

import akka.stm._

import scalaz._
import Scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.TestEventLog

class InvoiceService(initialState: Map[String, Invoice] = Map.empty) {
  val invoicesRef = Ref(initialState)
  val eventLog = TestEventLog[InvoiceEvent]()

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = invoicesRef().get(invoiceId)

  //
  // Updates
  //

  def createInvoice(invoiceId: String): DomainValidation[Invoice] = atomic {
    deferred { eventLog.store() }
    Invoice.create(invoiceId).result { (events, created) =>
      // transactional application state change
      invoicesRef alter { invoices => invoices + (invoiceId -> created) }
      // transactional event log update
      eventLog log events
    }
  }

  def updateInvoice(invoiceId: String, version: Option[Long])(f: Invoice => Update[InvoiceEvent, Invoice]): DomainValidation[Invoice] = atomic {
    deferred { eventLog.store() }
    invoicesRef().get(invoiceId) match {
      case None          => DomainError("no invoice with id %s").fail
      case Some(invoice) => (for {
        current <- invoice.require(version) // optional version check
        updated <- f(current)               // caller-supplied update
      } yield updated).result { (events, updated) =>
        // transactional application state change
        invoicesRef alter { invoices => invoices + (invoiceId -> updated) }
        // transactional event log update
        eventLog log events
      }
    }
  }

  def addInvoiceItem(invoiceId: String, version: Option[Long], invoiceItem: InvoiceItem) =
    updateInvoice(invoiceId, version) { invoice => invoice.addItem(invoiceItem) }

  def setInvoiceDiscount(invoiceId: String, version: Option[Long], discount: BigDecimal) =
    updateInvoice(invoiceId, version) { invoice => invoice.setDiscount(discount) }

  def sendInvoiceTo(invoiceId: String, version: Option[Long], to: InvoiceAddress) =
    updateInvoice(invoiceId, version) { invoice => invoice.sendTo(to) }
}

object InvoiceService {
  def apply(): InvoiceService =
    new InvoiceService

  def apply(history: List[InvoiceEvent]): InvoiceService =
    new InvoiceService(handle(history))

  def apply(initialState: Map[String, Invoice]): InvoiceService =
    new InvoiceService(initialState)

  def handle(events: List[InvoiceEvent]) = events.foldLeft(Map.empty[String, Invoice]) { (m, e) =>
    e match {
      case InvoiceCreated(invoiceId) => m + (invoiceId -> Invoice.handle(e))
      case event                     => m + (event.invoiceId -> m(event.invoiceId).handle(e))
    }
  }
}