// -*- mode: Scala;-*-
// Filename:    ReputationMessage.scala
// Authors:     lgm
// Creation:    Mon Jan 27 09:43:48 2014
// Copyright:   Not supplied
// Description:
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols.venue.msgs

import com.biosimilarity.evaluator.distribution.PortableAgentCnxn
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.protegra_ati.agentservices.protocols.msgs.{ProtocolMessage, SessionMsgStr}
import com.protegra_ati.agentservices.store.extensions.StringExtensions._

case class VenueCnxns(
  override val sessionId : String,
  override val correlationId : String,
  LgSrcCnxns: Seq[PortableAgentCnxn],
  LgTrgtCnxns: Seq[PortableAgentCnxn]
) extends VenueMessage(sessionId, correlationId) {
  override def toLabel : CnxnCtxtLabel[String,String,String] = {
    VenueCnxns.toLabel( sessionId )
  }
}


object VenueCnxns {
  def toLabel(): CnxnCtxtLabel[String, String, String] = {
    "protocolMessage(venueCnxns(sessionId(_)))".toLabel
  }

  def toLabel(sessionId: String): CnxnCtxtLabel[String, String, String] = {
    s"""protocolMessage(venueCnxns(sessionId(\"$sessionId\")))""".toLabel
  }
}