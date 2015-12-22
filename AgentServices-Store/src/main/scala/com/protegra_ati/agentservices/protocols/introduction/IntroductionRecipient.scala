package com.protegra_ati.agentservices.protocols

import com.biosimilarity.evaluator.distribution.ConcreteHL.PostedExpr
import com.biosimilarity.evaluator.distribution.{PortableAgentCnxn, PortableAgentBiCnxn}
import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.protegra_ati.agentservices.protocols.msgs._
import com.protegra_ati.agentservices.store.extensions.StringExtensions._
import java.util.UUID

trait IntroductionRecipientT extends Serializable {

  /**
    * Define a logger used within the protocol.
    * @note You can instantiate one with org.slf4j.LoggerFactory.getLogger(classOf[yourclass])
    * @return
    */
  def logger: org.slf4j.Logger

  private val biCnxnsListLabel = "biCnxnsList(true)".toLabel
  private val profileDataLabel = "jsonBlob(true)".toLabel

  def run(
    node: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    if (cnxns.size != 2) throw new Exception("invalid number of cnxns supplied")

    val protocolMgr = new ProtocolManager(node)
    val readCnxn = cnxns(0)
    val aliasCnxn = cnxns(1)

    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nIntroductionInitiator -- invoking listenGetIntroductionProfileRequest " 
        + "\nnode: " + protocolMgr.node
        + "\nreadCnxn: " + readCnxn
        + "\naliasCnxn: " + aliasCnxn        
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )

    listenGetIntroductionProfileRequest(protocolMgr, readCnxn, aliasCnxn)

    listenIntroductionRequest(protocolMgr, readCnxn, aliasCnxn)
  }

  private def listenGetIntroductionProfileRequest(protocolMgr: ProtocolManager, readCnxn: PortableAgentCnxn, aliasCnxn: PortableAgentCnxn): Unit = {
    // listen for GetIntroductionProfileRequest message
    val getIntroProfileRqLabel = GetIntroductionProfileRequest.toLabel()
    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nIntroductionRecipient -- waiting for GetIntroductionProfileRequest " 
        + "\nnode: " + protocolMgr.node
        + "\ncnxn: " + readCnxn
        + "\ngetIntroProfileRqLabel: " + getIntroProfileRqLabel
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )

    protocolMgr.subscribeMessage(readCnxn, getIntroProfileRqLabel, {
      case gIPR@GetIntroductionProfileRequest(sessionId, correlationId, rspCnxn) => {
        // TODO: get data from aliasCnxn once it is being stored there
        val identityCnxn = PortableAgentCnxn(aliasCnxn.src, "identity", aliasCnxn.trgt)

        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionRecipient -- reading profileData " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + identityCnxn
            + "\nprofileDataLabel: " + profileDataLabel
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )

        // get the profile data
        protocolMgr.read(identityCnxn, profileDataLabel, {
          case Some(mTT.RBoundAList(Some(mTT.Ground(PostedExpr(jsonBlob: String))), _)) => {
            val getIntroProfileRsp = GetIntroductionProfileResponse(sessionId, correlationId, jsonBlob)
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionRecipient -- sending GetIntroductionProfileResponse " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + rspCnxn
                + "\nGetIntroProfileRspLabel: " + getIntroProfileRsp.toLabel
                //+ "\nGetIntroductionProfileResponse: " + getIntroProfileRsp
                + "\nGetIntroductionProfileResponse: " + "..."
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )
            
            // send GetIntroductionProfileResponse message
            protocolMgr.putMessage(rspCnxn, getIntroProfileRsp)
          }
          case _ => throw new Exception("unexpected profile data")
        })
      }
    })
  }

  private def listenIntroductionRequest(protocolMgr: ProtocolManager, readCnxn: PortableAgentCnxn, aliasCnxn: PortableAgentCnxn): Unit = {
    // listen for IntroductionRequest message
    val introRqLabel = IntroductionRequest.toLabel()
    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nIntroductionRecipient -- waiting for IntroductionRequest " 
        + "\nnode: " + protocolMgr.node
        + "\ncnxn: " + readCnxn
        + "\ngetIntroProfileRqLabel: " + introRqLabel
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )

    protocolMgr.subscribeMessage(readCnxn, introRqLabel, {
      case ir@IntroductionRequest(sessionId, correlationId, rspCnxn, message, profileData) => {
        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionRecipient -- received IntroductionRequest " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + readCnxn
            + "\nintroRqLabel: " + introRqLabel
            //+ "\nIntroductionRequest: " + ir
            + "\nIntroductionRequest: " + "..."
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )

        val introN = IntroductionNotification(sessionId, UUID.randomUUID.toString, PortableAgentBiCnxn(readCnxn, rspCnxn), message, profileData)
        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionRecipient -- sending IntroductionNotification " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + aliasCnxn
            + "\nintroNLabel: " + introN.toLabel
            //+ "\nIntroductionNotification: " + introN
            + "\nIntroductionNotification: " + "..."
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        
        // send IntroductionNotification message
        protocolMgr.publishMessage(aliasCnxn, introN)
        
        // listen for IntroductionConfirmation message
        val introConfirmationLabel = IntroductionConfirmation.toLabel(sessionId, introN.correlationId)
        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionRecipient -- waiting for IntroductionConfirmation " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + aliasCnxn
            + "\nintroConfirmationLabel: " + introConfirmationLabel
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        
        protocolMgr.getMessage(aliasCnxn, introConfirmationLabel, {
          case ic@IntroductionConfirmation(_, _, true) => {
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionRecipient -- received IntroductionConfirmation " 
                + "\nIntroduction confirmed " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + aliasCnxn
                + "\nintroRqLabel: " + introConfirmationLabel
                + "\nIntroductionRequest: " + ic
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )

            val introRsp = IntroductionResponse(sessionId, correlationId, true, Some(UUID.randomUUID.toString))
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionRecipient -- sending IntroductionResponse " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + rspCnxn
                + "\nintroRspLabel: " + introRsp.toLabel
                + "\nIntroductionResponse: " + introRsp
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )            
            
            // send IntroductionResponse message
            protocolMgr.putMessage(rspCnxn, introRsp)
            
            // listen for Connect message
            val connectLabel = Connect.toLabel(sessionId, introRsp.connectCorrelationId.get)
            
            protocolMgr.getMessage(readCnxn, connectLabel, {
              case c@Connect(_, _, false, Some(newBiCnxn)) => {
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nIntroductionRecipient -- received Connect " 
                    + "\nnode: " + protocolMgr.node
                    + "\ncnxn: " + readCnxn
                    + "\nconnectLabel: " + connectLabel
                    + "\nConnect: " + c
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )

                def handleBiCnxnsList( prevBiCnxns : String ) : Unit = {
                  logger.debug(
                    (
                      "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      + "\nIntroductionRecipient -- received biCnxns list " 
                      + "\nnode: " + protocolMgr.node
                      + "\ncnxn: " + aliasCnxn
                      + "\nbiCnxnsListLabel: " + biCnxnsListLabel
                      + "\nprevBiCnxns: " + prevBiCnxns
                      + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    )
                  )

                  try {
                  
                    // add new biCnxn to the list
                    val newBiCnxns = newBiCnxn :: Serializer.deserialize[List[PortableAgentBiCnxn]](prevBiCnxns)
                    
                    // serialize biCnxn list
                    val newBiCnxnsStr = Serializer.serialize(newBiCnxns)
                    
                    // save new list of biCnxns
                    protocolMgr.put(aliasCnxn, biCnxnsListLabel, mTT.Ground(PostedExpr(newBiCnxnsStr)))
                    
                    val connectN = ConnectNotification(sessionId, newBiCnxn, profileData)

                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nIntroductionRecipient -- sending ConnectNotification " 
                        + "\nnode: " + protocolMgr.node
                        + "\ncnxn: " + aliasCnxn
                        + "\nconnectNLabel: " + connectN.toLabel
                        //+ "\nConnectionNotification: " + connectN
                        + "\nConnectionNotification: " + "..."
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                    // send ConnectNotification message
                    protocolMgr.publishMessage(aliasCnxn, connectN)
                  }
                  catch {
                    case e : Throwable => {
                      logger.error( "Encountered a problem when connecting an introduction...", e )
                    }
                  }
                }
                // get the list of biCnxns
                protocolMgr.get(aliasCnxn, biCnxnsListLabel, {
                  case Some(mTT.Ground(PostedExpr(prevBiCnxns: String))) => {
                    handleBiCnxnsList( prevBiCnxns )
                  }
                  case Some(mTT.RBoundAList(Some(mTT.Ground(PostedExpr(prevBiCnxns: String))), _)) => {
                    handleBiCnxnsList( prevBiCnxns )
                  }
                  case _ => throw new Exception("unexpected biCnxnsList data")
                })
              }
              case Connect(_, _, true, None) => ()
            })
          }
          case icAlt@IntroductionConfirmation(_, _, false) => {
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionRecipient -- received IntroductionConfirmation " 
                + "\nIntroduction denied " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + aliasCnxn
                + "\nintroRqLabel: " + introConfirmationLabel
                + "\nIntroductionRequest: " + icAlt
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )
            val introRsp = IntroductionResponse(sessionId, correlationId, false, None)
            
            // send IntroductionResponse message
            protocolMgr.putMessage(rspCnxn, introRsp)
          }
        })
      }
    })
  }
}

class IntroductionRecipient extends IntroductionRecipientT {

  val logger = org.slf4j.LoggerFactory.getLogger(classOf[IntroductionRecipient])

  override def run(
    kvdbNode: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    super.run(kvdbNode, cnxns, filters)
  }
}
