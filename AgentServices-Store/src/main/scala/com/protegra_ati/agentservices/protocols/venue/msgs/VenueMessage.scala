// -*- mode: Scala;-*-
// Filename:    ReputationMessage.scala
// Authors:     lgm
// Creation:    Mon Jan 27 09:43:48 2014
// Copyright:   Not supplied
// Description:
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols.venue.msgs

import com.protegra_ati.agentservices.protocols.msgs.{SessionMsgStr, ProtocolMessage}

abstract class VenueMessage(
  override val sessionId : String,
  override val correlationId : String
) extends ProtocolMessage with SessionMsgStr
