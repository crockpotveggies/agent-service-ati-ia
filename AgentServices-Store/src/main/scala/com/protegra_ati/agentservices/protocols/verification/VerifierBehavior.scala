// -*- mode: Scala;-*- 
// Filename:    VerifierBehavior.scala 
// Authors:     lgm                                                    
// Creation:    Mon Jan 27 10:29:48 2014 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols

import com.biosimilarity.evaluator.distribution.{PortableAgentCnxn, PortableAgentBiCnxn}
import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
import com.biosimilarity.evaluator.distribution.ConcreteHL.PostedExpr
import com.protegra_ati.agentservices.protocols.msgs._
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.biosimilarity.lift.lib._
import scala.util.continuations._
import java.util.UUID

trait VerifierBehaviorT extends ProtocolBehaviorT with Serializable {
  import com.biosimilarity.evaluator.distribution.utilities.DieselValueTrampoline._
  import com.protegra_ati.agentservices.store.extensions.StringExtensions._

  /**
    * Define a logger used within the protocol.
    * @note You can instantiate one with org.slf4j.LoggerFactory.getLogger(classOf[yourclass])
    * @return
    */
  def logger: org.slf4j.Logger

  def run(
    node : Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns : Seq[PortableAgentCnxn],
    filters : Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    // BUGBUG : lgm -- move defensive check on args to run method
    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nverifier -- behavior instantiated and run method invoked " 
        + "\nnode: " + node
        + "\ncnxns: " + cnxns
        + "\nfilters: " + filters
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )
    doVerification( node, cnxns )
  }

  def doVerification(
    node: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn]
  ): Unit = {
    val vrfr2GLoSWr :: agntCnxn :: rAgntCnxns =
      cnxns.map( { cnxn : PortableAgentCnxn => acT.AgentCnxn( cnxn.src, cnxn.label, cnxn.trgt ) } )
    val agntCnxns = agntCnxn :: rAgntCnxns

    for( cnxnRd <- agntCnxns ) {
      val cnxnWr = acT.AgentCnxn( cnxnRd.src, cnxnRd.label, cnxnRd.trgt )
      logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nverifier -- waiting for allow verification request on: " 
            + "\ncnxn: " + cnxnRd
            + "\nlabel: " + AllowVerification.toLabel
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
      )
      reset {
        for( eAllowV <- node.subscribe( cnxnRd )( AllowVerification.toLabel ) ) {
          rsrc2V[VerificationMessage]( eAllowV ) match {
            case Left( AllowVerification( sidAV, cidAV, rpAV, clmAV ) ) => {
              logger.debug(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nverifier -- received allow verification request: " + eAllowV
                  + "\ncnxn: " + cnxnRd
                  + "\nlabel: " + AllowVerification.toLabel
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )

              val agntRPRd =
                acT.AgentCnxn( rpAV.src, rpAV.label, rpAV.trgt )
              val agntRPWr =
                acT.AgentCnxn( rpAV.trgt, rpAV.label, rpAV.src )
              val acknowledgment =
                AckAllowVerification( sidAV, cidAV, rpAV, clmAV )

              logger.debug(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nverifier -- publishing AllowVerification acknowledgment: " + acknowledgment
                  + "\n on cnxn: " + cnxnWr
                  + "\n label: " + AllowVerification.toLabel( sidAV )
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )

              node.publish( cnxnWr )( 
                AckAllowVerification.toLabel( sidAV ),
                acknowledgment
              )

              logger.debug(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nverifier -- waiting for verify request on: " 
                  + "\ncnxn: " + agntRPWr
                  + "\nlabel: " + Verify.toLabel
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )

              for( eVerify <- node.subscribe( agntRPWr )( Verify.toLabel ) ) {
                rsrc2V[VerificationMessage]( eVerify ) match {
                  case Left( Verify( sidV, cidV, clmntV, clmV ) ) => {
                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nverifier -- received verification request: " + eVerify
                        + "\ncnxn: " + agntRPRd
                        + "\nlabel: " + Verify.toLabel
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )

                    if (
                      sidV.equals( sidAV ) && cidV.equals( cidAV )
                      && clmV.equals( clmAV ) // BUGBUG : lgm -- this is only a first
                                              // approximation. We can
                                              // use the full weight
                                              // of prolog here to
                                              // prove the claim.
                    ) {
                      val verification = 
                        Verification( sidV, cidV, clmntV, clmV, "claimVerified( true )".toLabel )
                      val notification =
                        VerificationNotification(
                          sidV, cidV, clmntV, clmV,
                          "claimVerified( true )".toLabel
                        )

                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nverifier -- verification request matches permission parameters" 
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )

                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nverifier -- publishing Verification testimony: " + verification
                          + "\n on cnxn: " + agntRPWr
                          + "\n label: " + Verification.toLabel( sidAV )
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )

                      node.publish( agntRPRd )(
                        Verification.toLabel( sidAV ),
                        verification
                      )

                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nverifier -- publishing VerificationNotification: " + notification
                          + "\n on cnxn: " + agntRPRd
                          + "\n label: " + VerificationNotification.toLabel( sidAV )
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )

                      node.publish( vrfr2GLoSWr )(
                        VerificationNotification.toLabel( sidAV ),
                        notification
                      )
                    }
                    else {
                      val verification =
                        Verification(
                          sidV, cidV, clmntV, clmV,
                          "protocolError(\"unexpected verify message data\")".toLabel 
                        )
                      val notification =
                        VerificationNotification(
                          sidV, cidV, clmntV, clmV,
                          "protocolError(\"unexpected verify message data\")".toLabel
                        )

                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nverifier -- publishing Verification disengagement: " + verification
                          + "\n on cnxn: " + agntRPWr
                          + "\n label: " + Verification.toLabel( sidAV )
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )
                      node.publish( agntRPWr )(
                        Verification.toLabel( sidAV ),
                        verification
                      )

                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nverifier -- publishing VerificationNotification: " + notification
                          + "\n on cnxn: " + agntRPWr
                          + "\n label: " + VerificationNotification.toLabel( sidAV )
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )

                      node.publish( vrfr2GLoSWr )(
                        VerificationNotification.toLabel( sidAV ),
                        notification
                      )
                    }
                  }
                  case Right( true ) => {
                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nverifier -- still waiting for verify request"
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                  }
                  case _ => {
                    val paCnxnRd =
                      PortableAgentCnxn( cnxnRd.src, cnxnRd.label, cnxnRd.trgt )
                    val notification =
                      VerificationNotification(
                        sidAV, cidAV, paCnxnRd, clmAV,
                        (
                          "protocolError(\"unexpected protocol message\","
                          + "\"" + eVerify + "\"" + ")"
                        ).toLabel
                      )

                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nverifier -- unexpected protocol message : " + eVerify
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nverifier -- publishing VerificationNotification: " + notification
                        + "\n on cnxn: " + agntRPWr
                        + "\n label: " + VerificationNotification.toLabel
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )

                    node.publish( vrfr2GLoSWr )(
                      VerificationNotification.toLabel(),
                      notification
                    )
                  }
                }
              }
            }
            case Right( true ) => {
              logger.debug(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nverifier -- still waiting for claim initiation"
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
            }
            case _ => {
              logger.error( "unexpected protocol message : " + eAllowV )
              node.publish( vrfr2GLoSWr )(
                VerificationNotification.toLabel(),
                VerificationNotification(
                  null, null, null, null,
                  (
                    "protocolError(\"unexpected protocol message\","
                    + "\"" + eAllowV + "\"" + ")"
                  ).toLabel
                )
              )
            }
          }
        }
      }
    }
  }
}

class VerifierBehavior(
) extends VerifierBehaviorT {

  val logger = org.slf4j.LoggerFactory.getLogger(classOf[VerifierBehavior])

  override def run(
    kvdbNode: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    super.run(kvdbNode, cnxns, filters)
  }
}

object VerifierBehavior {
  def apply( ) : VerifierBehavior = new VerifierBehavior()
  def unapply( cb : VerifierBehavior ) = Some( () )
}
