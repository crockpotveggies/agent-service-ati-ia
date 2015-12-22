// -*- mode: Scala;-*- 
// Filename:    Claimant.scala 
// Authors:     lgm                                                    
// Creation:    Tue Feb 11 11:00:15 2014 
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

trait ClaimantBehaviorT extends ProtocolBehaviorT with Serializable {
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
    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nclaimant -- behavior instantiated and run method invoked " 
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
    cnxns match {
      case clmnt2GLoS :: Nil => {
        val agntClmnt2GLoSRd =
          acT.AgentCnxn( clmnt2GLoS.src, clmnt2GLoS.label, clmnt2GLoS.trgt )
        val agntClmnt2GLoSWr =
          acT.AgentCnxn( clmnt2GLoS.trgt, clmnt2GLoS.label, clmnt2GLoS.src )

        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nclaimant -- waiting for initiate claim on: " 
            + "\ncnxn: " + agntClmnt2GLoSRd
            + "\nlabel: " + InitiateClaim.toLabel
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
  
        reset {
          for( eInitiateClaim <- node.subscribe( agntClmnt2GLoSRd )( InitiateClaim.toLabel ) ) {
            rsrc2V[VerificationMessage]( eInitiateClaim ) match {
              case Left( InitiateClaim( sidIC, cidIC, vrfrIC, rpIC, clmIC ) ) => {
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nclaimant -- received initiate claim request: " + eInitiateClaim
                    + "\ncnxn: " + agntClmnt2GLoSRd
                    + "\nlabel: " + InitiateClaim.toLabel
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )

                val agntVrfrRd =
                  acT.AgentCnxn( vrfrIC.src, vrfrIC.label, vrfrIC.trgt )
                val agntVrfrWr =
                  acT.AgentCnxn( vrfrIC.trgt, vrfrIC.label, vrfrIC.src )

                val avReq = AllowVerification( sidIC, cidIC, rpIC, clmIC )

                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nclaimant -- publishing AllowVerification request: " + avReq
                    + "\n on cnxn: " + agntVrfrWr
                    + "\n label: " + AllowVerification.toLabel( sidIC )
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )

                node.publish( agntVrfrRd )( 
                  AllowVerification.toLabel( sidIC ), 
                  avReq
                )

                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nclaimant -- waiting for allow verification acknowledgment on: " 
                    + "\ncnxn: " + agntVrfrRd
                    + "\nlabel: " + AckAllowVerification.toLabel( sidIC )
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
                for( eAllowV <- node.subscribe( agntVrfrRd )( AckAllowVerification.toLabel( sidIC ) ) ) {
                  rsrc2V[VerificationMessage]( eAllowV ) match {
                    case Left( AckAllowVerification( sidAAV, cidAAV, rpAAV, clmAAV ) ) => {
                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nclaimant -- received allow verification acknowledgment: " + eAllowV
                          + "\ncnxn: " + agntVrfrRd
                          + "\nlabel: " + AckAllowVerification.toLabel( sidIC )
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )
                      if (
                        sidAAV.equals( sidIC ) && cidAAV.equals( cidIC )
                        && rpAAV.equals( rpIC ) && clmAAV.equals( clmIC )
                      ) {
                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nclaimant -- allow verification acknowledgment matches request" 
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )
                        val agntRPWr =
                          acT.AgentCnxn( rpIC.src, rpIC.label, rpIC.trgt )
                        val agntRPRd =
                          acT.AgentCnxn( rpIC.trgt, rpIC.label, rpIC.src )

                        val ocReq = OpenClaim( sidIC, cidIC, rpIC, clmIC )

                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nclaimant -- publishing open claim request" + ocReq
                            + "\ncnxn: " + agntRPRd
                            + "\nlabel: " + OpenClaim.toLabel( sidIC )
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )

                        reset {
                          node.publish( agntRPWr )( 
                            OpenClaim.toLabel( sidIC ), 
                            ocReq
                          )            
                        }

                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nclaimant -- waiting for close claim on: " 
                            + "\ncnxn: " + agntRPRd
                            + "\nlabel: " + CloseClaim.toLabel( sidIC )
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )

                        for( eCloseClaim <- node.subscribe( agntRPRd )( CloseClaim.toLabel( sidIC ) ) ) {
                          rsrc2V[VerificationMessage]( eCloseClaim ) match {
                            case Left( CloseClaim( sidCC, cidCC, vrfrCC, clmCC, witCC ) ) => {
                              logger.debug(
                                (
                                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  + "\nclaimant -- received close claim message" + eCloseClaim
                                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                )
                              )
                              if (
                                sidCC.equals( sidIC ) && cidCC.equals( cidIC )
                                && vrfrCC.equals( rpIC ) && clmCC.equals( clmIC )
                              ) {
                                logger.debug(
                                  (
                                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                    + "\nclaimant -- close claim message matches open claim request"
                                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  )
                                )
                                logger.debug(
                                  (
                                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                    + "\nclaimant -- publishing complete claim message"
                                    + "\ncnxn: " + agntClmnt2GLoSWr
                                    + "\nlabel: " + CompleteClaim.toLabel( sidIC )
                                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  )
                                )
                                node.publish( agntClmnt2GLoSWr )(
                                  CompleteClaim.toLabel( sidIC ),
                                  CompleteClaim( sidCC, cidCC, vrfrCC, clmCC, witCC )
                                )
                              }
                              else {
                                logger.debug(
                                  (
                                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                    + "\nclaimant -- close doesn't match open : " + eCloseClaim
                                    + "\nsidIC : " + sidIC + " sidCC : " + sidCC
                                    + "\nsidCC.equals( sidIC ) : " + sidCC.equals( sidIC )
                                    + "\ncidIC : " + cidIC + " cidCC : " + cidCC
                                    + "\ncidCC.equals( cidIC ) : " + cidCC.equals( cidIC )
                                    + "\nrpIC : " + rpIC + " vrfrCC : " + vrfrCC
                                    + "\nvrfrCC.equals( vrfrIC ) : " + vrfrCC.equals( vrfrIC )
                                    + "\nclmIC : " + clmIC + "clmCC : " + clmCC
                                    + "\nclmCC.equals( clmIC ) : " + clmCC.equals( clmIC )
                                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  )
                                )
                                node.publish( agntClmnt2GLoSWr )(
                                  CompleteClaim.toLabel( sidIC ),
                                  CompleteClaim(
                                    sidIC, cidIC, vrfrIC, clmIC,
                                    "protocolError(\"relying party close claim does not match open claim\")".toLabel
                                  )
                                )
                              }
                            }
                            case Right( true ) => {
                              BasicLogService.tweet(
                                (
                                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  + "\nclaimant -- still waiting for close claim"
                                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                )
                              )
                              println(
                                (
                                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  + "\nclaimant -- still waiting for close claim"
                                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                )
                              )
                            }
                            case _ => {
                              // BUGBUG : lgm -- protect against strange and
                              // wondrous toString implementations (i.e. injection
                              // attack ) for eInitiateClaim
                              logger.debug(
                                (
                                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                  + "\nclaimant -- while waiting for close claim"
                                  + "\nunexpected protocol message : " + eCloseClaim
                                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                                )
                              )
                              node.publish( agntClmnt2GLoSWr )(
                                VerificationNotification.toLabel( sidIC ),
                                VerificationNotification(
                                  sidIC, cidIC, clmnt2GLoS, clmIC,
                                  (
                                    "protocolError(\"unexpected protocol message\","
                                    + "\"" + eCloseClaim + "\"" + " while waiting for close claim " + ")"
                                  ).toLabel
                                )
                              )
                            }
                          }
                        }
                      }
                      else {
                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nclaimant -- ack doesn't match : " + eAllowV
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )
                        node.publish( agntClmnt2GLoSWr )(
                          CompleteClaim.toLabel( sidIC ),
                          CompleteClaim(
                            sidIC, cidIC, vrfrIC, clmIC,
                            "protocolError(\"verifier acknowledgment does not match allow verification\")".toLabel
                          )
                        )
                      }
                    }
                    case Right( true ) => {
                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nclaimant -- still waiting for allow claim ack"
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )
                    }
                    case _ => {
                      logger.debug(
                        (
                          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          + "\nclaimant -- while waiting for acknowledgment of allow verification"
                          + "\nunexpected protocol message : " + eAllowV
                          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        )
                      )
                      node.publish( agntClmnt2GLoSWr )(
                        VerificationNotification.toLabel( sidIC ),
                        VerificationNotification(
                          sidIC, cidIC, clmnt2GLoS, clmIC,
                          (
                            "protocolError(\"unexpected protocol message\","
                             + "\"" + eAllowV + "\"" + " while waiting for allow verification acknowledgment " + ")"
                          ).toLabel
                        )
                      )
                    }
                  }
                }
              }
              case Right( true ) => {
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nclaimant -- still waiting for claim initiation"
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
              }
              case _ => {
                // BUGBUG : lgm -- protect against strange and
                // wondrous toString implementations (i.e. injection
                // attack ) for eInitiateClaim
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nclaimant -- while waiting for initiate claim"
                    + "\nreceived unexpected protocol message : " + eInitiateClaim
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
                node.publish( agntClmnt2GLoSWr )(
                  VerificationNotification.toLabel(),
                  VerificationNotification(
                    null, null, null, null,
                    (
                      "protocolError(\"unexpected protocol message\","
                      + "\"" + eInitiateClaim + "\"" + " while waiting for InitiateClaim" + ")"
                    ).toLabel
                  )
                )
              }
            }
          }
        }
      }
      case _ => {
        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nclaimant -- one cnxn expected : " + cnxns
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        throw new Exception( "one cnxn expected : " + cnxns )
      }
    }
  }
}

class ClaimantBehavior(
) extends ClaimantBehaviorT {

  val logger = org.slf4j.LoggerFactory.getLogger(classOf[ClaimantBehavior])

  override def run(
    kvdbNode: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    super.run(kvdbNode, cnxns, filters)
  }
}

object ClaimantBehavior {
  def apply( ) : ClaimantBehavior = new ClaimantBehavior()
  def unapply( cb : ClaimantBehavior ) = Some( () )
}
