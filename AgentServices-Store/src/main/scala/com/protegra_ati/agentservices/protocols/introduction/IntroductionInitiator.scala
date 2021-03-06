package com.protegra_ati.agentservices.protocols

import com.biosimilarity.evaluator.distribution.{PortableAgentCnxn, PortableAgentBiCnxn}
import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.protegra_ati.agentservices.protocols.msgs._
import java.util.UUID

trait IntroductionInitiatorT extends Serializable {

  /**
    * Define a logger used within the protocol.
    * @note You can instantiate one with org.slf4j.LoggerFactory.getLogger(classOf[yourclass])
    * @return
    */
  def logger: org.slf4j.Logger

  def run(
    node: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    if (cnxns.size != 1) throw new Exception("invalid number of cnxns supplied")

    val protocolMgr = new ProtocolManager(node)
    val aliasCnxn = cnxns(0)

    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nIntroductionInitiator -- invoking listenBeginIntroductionRequest " 
        + "\nnode: " + protocolMgr.node
        + "\ncnxn: " + aliasCnxn
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )

    listenBeginIntroductionRequest(protocolMgr, aliasCnxn)
  }

  private def listenBeginIntroductionRequest(protocolMgr: ProtocolManager, aliasCnxn: PortableAgentCnxn): Unit = {
    // listen for BeginIntroductionRequest message
    val beginIntroRqLabel = BeginIntroductionRequest.toLabel()
    logger.debug(
      (
        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        + "\nIntroductionInitiator -- waiting for BeginIntroductionRequest " 
        + "\nnode: " + protocolMgr.node
        + "\ncnxn: " + aliasCnxn
        + "\nbeginIntroRqLabel: " + beginIntroRqLabel
        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
      )
    )
    protocolMgr.subscribeMessage(aliasCnxn, beginIntroRqLabel, {
      case bIRRq@BeginIntroductionRequest(sessionId, PortableAgentBiCnxn(aReadCnxn, aWriteCnxn), PortableAgentBiCnxn(bReadCnxn, bWriteCnxn), aMessage, bMessage) => {
        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionInitiator -- received BeginIntroductionRequest " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + aliasCnxn
            + "\nbeginIntroRqLabel: " + beginIntroRqLabel
            + "\nBeginIntroductionRequest: " + bIRRq
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        val aGetIntroProfileRq = GetIntroductionProfileRequest(sessionId, UUID.randomUUID.toString, aReadCnxn)

        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionInitiator -- sending A's GetIntroductionProfileRequest " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + aWriteCnxn
            + "\naGetIntroProfileRqLabel: " + aGetIntroProfileRq.toLabel
            + "\nGetIntroductionProfileRequest: " + aGetIntroProfileRq
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        
        // send A's GetIntroductionProfileRequest message
        protocolMgr.publishMessage(aWriteCnxn, aGetIntroProfileRq)
        
        // listen for A's GetIntroductionProfileResponse message
        val aGetIntroProfileRspLabel = GetIntroductionProfileResponse.toLabel(sessionId, aGetIntroProfileRq.correlationId)

        logger.debug(
          (
            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
            + "\nIntroductionInitiator -- waiting for A's GetIntroductionProfileResponse " 
            + "\nnode: " + protocolMgr.node
            + "\ncnxn: " + aReadCnxn
            + "\naGetIntroProfileRspLabel: " + aGetIntroProfileRspLabel
            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          )
        )
        protocolMgr.getMessage(aReadCnxn, aGetIntroProfileRspLabel, {
          case aGIRPRsp@GetIntroductionProfileResponse(_, _, aProfileData) => {
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionInitiator -- received A's GetIntroductionProfileResponse " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + aReadCnxn
                + "\naGetIntroProfileRspLabel: " + aGetIntroProfileRspLabel
                //+ "\nGetIntroductionProfileResponse: " + aGIRPRsp
                + "\nGetIntroductionProfileResponse: " + "..."
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )
            val bGetIntroProfileRq = GetIntroductionProfileRequest(sessionId, UUID.randomUUID.toString, bReadCnxn)
            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionInitiator -- sending B's GetIntroductionProfileRequest " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + bWriteCnxn
                + "\naGetIntroProfileRqLabel: " + bGetIntroProfileRq.toLabel
                + "\nGetIntroductionProfileRequest: " + bGetIntroProfileRq
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )
            
            
            // send B's GetIntroductionProfileRequest message
            protocolMgr.publishMessage(bWriteCnxn, bGetIntroProfileRq)
            
            // listen for B's GetIntroductionProfileResponse message
            val bGetIntroProfileRspLabel = GetIntroductionProfileResponse.toLabel(sessionId, bGetIntroProfileRq.correlationId)

            logger.debug(
              (
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nIntroductionInitiator -- waiting for B's GetIntroductionProfileResponse " 
                + "\nnode: " + protocolMgr.node
                + "\ncnxn: " + bReadCnxn
                + "\naGetIntroProfileRspLabel: " + bGetIntroProfileRspLabel
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            )
            protocolMgr.getMessage(bReadCnxn, bGetIntroProfileRspLabel, {          
              case bGIRPRsp@GetIntroductionProfileResponse(_, _, bProfileData) => {
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nIntroductionInitiator -- received B's GetIntroductionProfileResponse " 
                    + "\nnode: " + protocolMgr.node
                    + "\ncnxn: " + bReadCnxn
                    + "\naGetIntroProfileRspLabel: " + bGetIntroProfileRspLabel
                    + "\nGetIntroductionProfileResponse: " + "..."
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
                val aIntroRq = IntroductionRequest(sessionId, UUID.randomUUID.toString, aReadCnxn, aMessage, bProfileData)

                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nIntroductionInitiator -- sending A's IntroductionRequest " 
                    + "\nnode: " + protocolMgr.node
                    + "\ncnxn: " + aWriteCnxn
                    + "\naIntroRqLabel: " + aIntroRq.toLabel
                    //+ "\nIntroductionRequest: " + aIntroRq
                    + "\nIntroductionRequest: " + "..."
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
                // send A's IntroductionRequest message
                protocolMgr.publishMessage(aWriteCnxn, aIntroRq)
                
                // listen for A's IntroductionResponse message
                val aIntroRspLabel = IntroductionResponse.toLabel(sessionId, aIntroRq.correlationId)
                logger.debug(
                  (
                    "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                    + "\nIntroductionInitiator -- waiting for A's IntroductionResponse " 
                    + "\nnode: " + protocolMgr.node
                    + "\ncnxn: " + aReadCnxn
                    + "\naIntroRspLabel: " + aIntroRspLabel
                    + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  )
                )
                
                protocolMgr.getMessage(aReadCnxn, aIntroRspLabel, {
                  case aIR@IntroductionResponse(_, _, aAccepted, aConnectCorrelationId) => {
                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nIntroductionInitiator -- received A's IntroductionResponse " 
                        + "\nnode: " + protocolMgr.node
                        + "\ncnxn: " + aReadCnxn
                        + "\naGetIntroProfileRspLabel: " + aIntroRspLabel
                        + "\nGetIntroductionProfileResponse: " + "..."
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                    val bIntroRq = IntroductionRequest(sessionId, UUID.randomUUID.toString, bReadCnxn, bMessage, aProfileData)

                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nIntroductionInitiator -- sending b's IntroductionRequest " 
                        + "\nnode: " + protocolMgr.node
                        + "\ncnxn: " + bWriteCnxn
                        + "\nbIntroRqLabel: " + bIntroRq.toLabel
                        + "\nIntroductionRequest: " + "..."
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                    
                    // send B's IntroductionRequest message
                    protocolMgr.publishMessage(bWriteCnxn, bIntroRq)
                    
                    // listen for B's IntroductionResponse message
                    val bIntroRspLabel = IntroductionResponse.toLabel(sessionId, bIntroRq.correlationId)
                    logger.debug(
                      (
                        "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                        + "\nIntroductionInitiator -- waiting for B's IntroductionResponse " 
                        + "\nnode: " + protocolMgr.node
                        + "\ncnxn: " + bReadCnxn
                        + "\naIntroRspLabel: " + bIntroRspLabel
                        + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                      )
                    )
                    
                    protocolMgr.getMessage(bReadCnxn, bIntroRspLabel, {
                      case bIR@IntroductionResponse(_, _, bAccepted, bConnectCorrelationId) => {
                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nIntroductionInitiator -- received B's IntroductionResponse " 
                            + "\nnode: " + protocolMgr.node
                            + "\ncnxn: " + bReadCnxn
                            + "\naGetIntroProfileRspLabel: " + bIntroRspLabel
                            + "\nGetIntroductionProfileResponse: " + "..."
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )


                        logger.debug(
                          (
                            "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            + "\nIntroductionInitiator -- checking transaction status " 
                            + "\nnode: " + protocolMgr.node
                            + "\naAccepted: " + aAccepted
                            + "\nbAccepted: " + bAccepted
                            + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                          )
                        )
                        // check whether A and B accepted
                        if (aAccepted && bAccepted) {
                          // println(
//                             (
//                               "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
//                               + "\nIntroductionInitiator -- both parties committed " 
//                               + "\nnode: " + protocolMgr.node
//                               + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
//                             )
//                           )
                          // create new cnxns
                          val cnxnLabel = UUID.randomUUID().toString
                          val abCnxn = PortableAgentCnxn(aReadCnxn.src, cnxnLabel, bReadCnxn.src)
                          val baCnxn = PortableAgentCnxn(bReadCnxn.src, cnxnLabel, aReadCnxn.src)
                          val aNewBiCnxn = PortableAgentBiCnxn(baCnxn, abCnxn)
                          val bNewBiCnxn = PortableAgentBiCnxn(abCnxn, baCnxn)
                          
                          val aConnect = Connect(sessionId, aConnectCorrelationId.get, false, Some(aNewBiCnxn))
                          val bConnect = Connect(sessionId, bConnectCorrelationId.get, false, Some(bNewBiCnxn))
                          
                          logger.debug(
                            (
                              "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                              + "\nIntroductionInitiator -- sending both Connect Msgs" 
                              + "\nnode: " + protocolMgr.node
                              + "\naCnxn: " + aWriteCnxn
                              + "\nbCnxn: " + bWriteCnxn
                              + "\naConnectLabel: " + aConnect.toLabel
                              + "\nbConnectLabel: " + bConnect.toLabel
                              + "\naConnect: " + aConnect
                              + "\nbConnect: " + bConnect
                              + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            )
                          )
                          
                          // send Connect messages
                          protocolMgr.putMessage(aWriteCnxn, aConnect)
                          protocolMgr.putMessage(bWriteCnxn, bConnect)
                        } else if (aAccepted) {
                          val aConnect = Connect(sessionId, aConnectCorrelationId.get, true, None)
                          logger.debug(
                            (
                              "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                              + "\nIntroductionInitiator -- alerting A's UI" 
                              + "\nnode: " + protocolMgr.node
                              + "\naCnxn: " + aWriteCnxn
                              + "\naConnectLabel: " + aConnect.toLabel
                              + "\naConnect: " + aConnect
                              + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            )
                          )
                          
                          // send Connect message
                          protocolMgr.putMessage(aWriteCnxn, aConnect)
                        } else if (bAccepted) {
                          val bConnect = Connect(sessionId, bConnectCorrelationId.get, true, None)
                          logger.debug(
                            (
                              "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                              + "\nIntroductionInitiator -- alerting B's UI" 
                              + "\nnode: " + protocolMgr.node
                              + "\naCnxn: " + bWriteCnxn
                              + "\naConnectLabel: " + bConnect.toLabel
                              + "\naConnect: " + bConnect
                              + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                            )
                          )
                          
                          
                          // send Connect message
                          protocolMgr.putMessage(bWriteCnxn, bConnect)
                        }
                      }
                    })
                  }
                })
              }
            })
          }
        })
      }
    })
  }
}

class IntroductionInitiator extends IntroductionInitiatorT {

  val logger = org.slf4j.LoggerFactory.getLogger(classOf[IntroductionInitiator])

  override def run(
    kvdbNode: Being.AgentKVDBNode[PersistedKVDBNodeRequest, PersistedKVDBNodeResponse],
    cnxns: Seq[PortableAgentCnxn],
    filters: Seq[CnxnCtxtLabel[String, String, String]]
  ): Unit = {
    super.run(kvdbNode, cnxns, filters)
  }
}
