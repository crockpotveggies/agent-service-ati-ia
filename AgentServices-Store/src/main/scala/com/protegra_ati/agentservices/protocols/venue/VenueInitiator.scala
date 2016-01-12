// -*- mode: Scala;-*-
// Filename:    Claimant.scala
// Authors:     lgm
// Creation:    Tue Feb 11 11:00:15 2014
// Copyright:   Not supplied
// Description:
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols.venue

import com.biosimilarity.evaluator.distribution.{PortableAgentCnxn, PortableAgentBiCnxn}
import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
import com.biosimilarity.evaluator.distribution.ConcreteHL.PostedExpr
import com.protegra_ati.agentservices.protocols.ProtocolBehaviorT
import com.protegra_ati.agentservices.protocols.msgs._
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.biosimilarity.lift.lib._
import com.protegra_ati.agentservices.protocols.venue.msgs.{VenueCnxns, VenueMessage}
import scala.util.continuations._
import java.util.UUID

trait VenueInitiatorT extends ProtocolBehaviorT with Serializable {
  import com.biosimilarity.evaluator.distribution.utilities.DieselValueTrampoline._
  import com.protegra_ati.agentservices.store.extensions.StringExtensions._

  /**
    * Define a logger used within the protocol.
    * @note You can instantiate one with org.slf4j.LoggerFactory.getLogger(classOf[yourclass])
    * @return
    */
  def logger: org.slf4j.Logger

  // TODO: need to update config file similar to Greg's email so this class is started up
  def run(
    node : Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns : Seq[PortableAgentCnxn],
    filters : Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    logger.debug(
      s"""||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
      initiate venue -- behavior instantiated and run method invoked
      node: $node
      cnxns: $cnxns
      filters: $filters
      ||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"""
    )

    initiateVenueProtocol( node, cnxns, filters )
  }

  // node + connections + filters = a URL (that's the way it's designed)
  def initiateVenueProtocol(
     node : Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
     cnxns : Seq[PortableAgentCnxn],
     filters : Seq[CnxnCtxtLabel[String, String, String]]
  ) = {

    val lGCnxnsLabel :: forwardingLabel :: Nil = filters
    val cnxn :: Nil = cnxns
    // when we get a connection, we convert it to the type that's required
    val agentCnxn =
      acT.AgentCnxn( cnxn.src, cnxn.label, cnxn.trgt )

    //
    def srcCnxn(portableAgentCnxn: PortableAgentCnxn) = {
      PortableAgentCnxn(portableAgentCnxn.trgt, portableAgentCnxn.label, portableAgentCnxn.src)
    }

    reset {

      for( lGCnxnsMsg <- node.get( agentCnxn )( lGCnxnsLabel ) ) {
        rsrc2V[VenueMessage]( lGCnxnsMsg ) match { // connections label is going to be found through pattern matching on the cnxns
          case Left( VenueCnxns( _, _, lGTrgtCnxns, lGSrcCnxns ) ) => {
            for( cnxnTrgt <- lGTrgtCnxns ) {
              // need to convert this to the type of connection required by the parameter
              val agentCnxnT =
                acT.AgentCnxn( cnxnTrgt.src, cnxnTrgt.label, cnxnTrgt.trgt )
              reset {
                for( msg <- node.subscribe( agentCnxnT )( forwardingLabel ) ) {
                  // we have to massage the compiler by doing this
                  val newConnectionList = lGSrcCnxns.diff(List(srcCnxn( cnxnTrgt )) )
                  
                  val collItr = newConnectionList.iterator
                  while( collItr.hasNext ) {
                    val cnxnS = collItr.next
                    reset {
                      node.publish(
                        acT.AgentCnxn( cnxnS.src, cnxnS.label, cnxnS.trgt )
                      )( forwardingLabel, msg )
                    }
                  };
                  ()
                }
              }
            }
          }
          case Right( true ) => {
            // There's no cnxns. We'll go to sleep until there are venue cnxns.
          }
          case _ => {
            // We got an unexpected message.
          }
        }
      }
    }
  }
}

class VenueInitiator(
) extends VenueInitiatorT {

  val logger = org.slf4j.LoggerFactory.getLogger(classOf[VenueInitiator])

  override def run(
    kvdbNode: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    super.run(kvdbNode, cnxns, filters)
  }
}

object VenueInitiator {
  def apply( ) : VenueInitiator = new VenueInitiator()
  def unapply( cb : VenueInitiator ) = Some( () )
}
