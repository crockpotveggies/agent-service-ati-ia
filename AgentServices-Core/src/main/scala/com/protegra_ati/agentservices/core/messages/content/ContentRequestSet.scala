package com.protegra_ati.agentservices.core.messages.content

/* User: jklassen
*/

import com.protegra.agentservicesstore.extensions.StringExtensions._
import com.protegra_ati.agentservices.core.extensions.ClassExtensions._
import com.protegra_ati.agentservices.core.platformagents._
import com.protegra_ati.agentservices.core.schema.behaviors.Tracking
import com.protegra_ati.agentservices.core.platformagents.behaviors._
import com.protegra.agentservicesstore.usage.AgentKVDBScope.acT._
import com.protegra_ati.agentservices.core.schema._
import com.protegra_ati.agentservices.core.messages._
import com.protegra.agentservicesstore.util._
import com.protegra_ati.agentservices.core.schema._
import org.joda.time.{DateTime, Instant}
import scala.collection.JavaConversions._
import util.ConnectionFactory

trait ContentRequestSet
{
  self: AgentHostStorePlatformAgent =>
  //    with Storage
  //    with Notifications
  //    with Authorization

  def listenPublicContentRequest(cnxn: AgentCnxnProxy) =
  {
    listen(_publicQ, cnxn, Channel.Content, ChannelType.Request, ChannelLevel.Public, handlePublicContentRequestChannel(_: AgentCnxnProxy, _: Message))
  }

  def handlePublicContentRequestChannel[ T <: Data ](cnxn: AgentCnxnProxy, msg: Message) =
  {
    report("entering handlePrivateContentRequestChannel in StorePlatform", Severity.Trace)
    msg match {
      case x: GetContentRequest => {
        if ( isLocalNetworkMode() || _cnxnUserSelfConnectionsList.contains(x.targetCnxn) || authorizationNotRequired(x) ) {
          processGetContentRequest(x)
        }
        else{
          authorizeRequest(x, false)
        }
      }
      case x: SetContentRequest => {
        processSetContentRequest(x)
      }
      case x: SetContentAdminRequest => {
        processSetContentAdminRequest(x)
      }
      case x: SetContentAuthorizationRequest => {
        processSetContentAuthorizationRequest(x)
      }
      case x: DeleteContentRequest => {
        processDeleteContentRequest(x)
      }
      case x: SetContentPersistedRequest => {
        processSetContentPersistedRequest(x)
      }
      case x: SetSelfContentRequest => {
        processSelfContentRequest(cnxn, x)
      }
      case _ => report("***********************not doing anything in handlePrivateContentRequestChannel", Severity.Error)
    }
    report("exiting handlePrivateContentRequestChannel in StorePlatform", Severity.Trace)
  }

  def processGetContentRequest(msg: GetContentRequest) =
  {
    report("entering processGetContentRequest in StorePlatform", Severity.Trace)
    // println ("processGetContentRequest   " +msg.queryObject)
    msg.queryObject match {
      case x: CompositeData[ _ ] => {
        //although x is part of the msg, there is no point in having to match it again
        processGetContentRequestComposite(x, msg)
      }
      case x: Data => {
        //simple search
        fetchList[ Data ](_dbQ, msg.targetCnxn, msg.queryObject.toSearchKey, handleGetContentRequestFetch(_: AgentCnxnProxy, _: List[ Data ], msg.originCnxn, msg))
      }
      case _ => report("***********************unknown msg.queryObject encountered in processGetContentRquest", Severity.Error)

    }
    //get from db and put Response for AgentHostUIPlatformAgent on queue


    //send messages between PAs
    //val publicRequest = new GetContentRequest(x.queryObject)
    //publicRequest.parentId = x.id
    //send(_publicQ, x.targetCnxn, publicRequest)
  }

  def processGetContentRequestComposite(search: CompositeData[ _ <: Data ], msg: GetContentRequest) =
  {
    //we need a list of connections from the targetcnxn and for each of those cnxns, we preform a query for the requested data
    //one response will be generated per connection but containing a list of all the found data
    //if a single conn id is set in the query could optimize if we really want not to pull back list of conn
    fetch[ Connection ](_dbQ, msg.targetCnxn, search.connection.toSearchKey, handleGetContentRequestCompositeByConnectionFetch(_: AgentCnxnProxy, _: Connection, search, msg))
  }

  def handleGetContentRequestCompositeByConnectionFetch(cnxn: AgentCnxnProxy, connection: Connection, search: CompositeData[ _ <: Data ], msg: GetContentRequest)
  {
    //need to take in a flag if we start getting conflicts on connection.writeCnxn/connection.readCnxn choices
    //audit log requires connection.writeCnxn
    //audit log is an exception in that it is info generated by the reader of our data, not generated by us writing data to selfCnxn which is then duplicated to writeCnxn
    fetchList[ Data ](_dbQ, lookupConnection(search, connection), search.data.toSearchKey, handleGetContentRequestCompositeFetch(_: AgentCnxnProxy, _: List[ Data ], msg.originCnxn, msg, connection))
  }

  def handleGetContentRequestCompositeFetch(cnxn: AgentCnxnProxy, data: List[ Data ], originCnxn: AgentCnxnProxy, parent: Message, connection: Connection)
  {
    //no audit log generation for these types of requests for now
    val compositeData = data.map(datum => new CompositeData[ datum.type ](connection, datum))
    val response = new GetContentResponse(parent.ids.copyAsChild(), parent.eventKey.copy(), compositeData)
    response.originCnxn = originCnxn
    response.targetCnxn = parent.targetCnxn
    send(_publicQ, originCnxn, response)

    report("exiting handleGetContentRequestCompositeFetch", Severity.Trace)

  }

  def handleGetContentRequestFetch(cnxn: AgentCnxnProxy, data: List[ Data ], originCnxn: AgentCnxnProxy, parent: Message)
  {
    report("entering handleFetch in StorePlatform", Severity.Trace)
    //don't log audit trail for self cnxn's
    data.foreach(datum => {
      if ( !_cnxnUserSelfConnectionsList.contains(cnxn) ) {
        logDataViewed(cnxn, datum, originCnxn)
      }
    })

    val response = new GetContentResponse(parent.ids.copyAsChild(), parent.eventKey.copy(), data)
    response.originCnxn = originCnxn
    response.targetCnxn = parent.targetCnxn
    send(_publicQ, originCnxn, response)

    report("exiting handleFetch", Severity.Trace)
  }

  def processSetContentAdminRequest(msg: SetContentAdminRequest)
  {
    report("entering processSetContentAdminRequest in StorePlatform", Severity.Trace)

    msg.newData match {
      case x: Connection => {
        updateDataById(_storeCnxn, msg.newData)
        if ( isNew(msg.newData, msg.oldData) ) {
          //create the systemdata
          val selfCnxn = x.writeCnxn
          generateSystemData(selfCnxn, x)

          //if new we need to add it to the list and start listening
          //register self connection in a self connection list
          addToHostedCnxn(selfCnxn)
          //appBizNetwork Conn just needs to listen to it's 1 new conn
          if (isDistributedNetworkMode())
            listenForHostedCnxn(selfCnxn)
        }
      }
      case _ => {
      }
    }


    val responseEventKey = if ( msg.eventKey == null ) null else msg.eventKey.copy()
    val response = new SetContentAdminResponse(msg.ids.copyAsChild(), responseEventKey, msg.newData)
    response.originCnxn = msg.originCnxn
    response.targetCnxn = msg.targetCnxn
    report("Store sending msg on private queue cnxn:" + msg.originCnxn.toString)
    send(_publicQ, msg.originCnxn, response)

    report("exiting processSetContentAdminRequest in StorePlatform", Severity.Trace)
  }

  def isNew(newData: Data, oldData: Data): Boolean =
  {
    return ( newData != null && oldData == null )
  }

  def processSetContentRequest(msg: SetContentRequest)
  {
    report("entering processSetContentRequest in StorePlatform", Severity.Trace)
    //refactor newData better
    var newData: Data = null;
    msg.newData match {
      case x: CompositeData[ Data ] => {
        newData = x.data;
        newData match {
          case tracked: Data with Tracking => {
            tracked.deliver()
          }
          case _ => {}
        }

        setContentForSelfAndForCompositeConnections(msg.targetCnxn, msg.ids, msg.eventKey, x, msg.oldData)
      }
      case y: Data => {
        newData = msg.newData
        newData match {
          case tracked: Data with Tracking => {
            tracked.deliver()
          }
          case _ => {}
        }
        //standard save functionality
        setContentIfDisclosedDataChanged(msg)
        setContentForSelfAndAllConnectionsAndTargetSelf(msg.targetCnxn, msg.ids, msg.eventKey, msg.newData, msg.oldData)
        setContentIfConnectionChanged(msg)
      }

      val response = new SetContentResponse(msg.ids.copyAsChild(), msg.eventKey.copy(), newData)
      response.originCnxn = msg.originCnxn
      response.targetCnxn = msg.targetCnxn
      report("Store sending msg on private queue cnxn:" + msg.originCnxn.toString)
      send(_publicQ, msg.originCnxn, response)

      case _ => report("***********************unknown msg.queryObject encountered in processGetContentRquest:" + msg.newData, Severity.Error)
    }

    report("exiting processSetContentRequest in StorePlatform", Severity.Trace)

  }

  def setContentForSelfAndAllConnections(selfCnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data)
  {
    //store object on self Cnxn
    updateDataById(selfCnxn, newData)

    //replicate data on all connections with appropriate level of disclosure based on connectionType
    //if no disclosed data is found for an object type (e.g. profile), then nothing is replicated on the related connections for that data
    //val contentSearch = SearchFactory.getDisclosedDataSearchItem(newData)

    val contentSearch = new DisclosedData(newData.getClassOf, "", "")
    //    newData:Profile => .getClassOf => Class[Profile]
    //  newData:Profile => .getClass => java.lang.Class<Profile>   != Class[Profile]

    fetch[ Data ](_dbQ, selfCnxn, contentSearch.toSearchKey, handleSetContentForAllConnectionsFetch(_: AgentCnxnProxy, _: Data, parentRequestIds, parentRequestEventKey, newData, oldData))
  }

  def setContentForSelfAndAllConnectionsAndTargetSelf(selfCnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data)
  {
    //store object on self Cnxn
    updateDataById(selfCnxn, newData)

    //replicate data on all connections with appropriate level of disclosure based on connectionType
    //if no disclosed data is found for an object type (e.g. profile), then nothing is replicated on the related connections for that data
    //val contentSearch = SearchFactory.getDisclosedDataSearchItem(newData)

    val contentSearch = new DisclosedData(newData.getClassOf, "", "")
    //    newData:Profile => .getClassOf => Class[Profile]
    //  newData:Profile => .getClass => java.lang.Class<Profile>   != Class[Profile]

    fetch[ Data ](_dbQ, selfCnxn, contentSearch.toSearchKey, handleSetContentForAllConnectionsAndTargetSelfFetch(_: AgentCnxnProxy, _: Data, parentRequestIds, parentRequestEventKey, newData, oldData))
  }

  protected def handleSetContentForAllConnectionsAndTargetSelfFetch(cnxn: AgentCnxnProxy, disclosedData: Data, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data)
  {
    report("entering handleSetContentForAllConnectionsFetch in StorePlatform", Severity.Trace)

    disclosedData match {
      case x: DisclosedData[ Data ] => {
        report("processSetContentRequest: found authorizedContent - connection type: " + x.connectionType)
        setContentByConnectionTypeAndToTargetSelf(cnxn, parentRequestIds, parentRequestEventKey, newData, oldData, x)
      }
      case _ => {
      }
    }

    report("exiting handleSetContentForAllConnectionsFetch", Severity.Trace)
  }


  protected def handleSetContentForAllConnectionsFetch(cnxn: AgentCnxnProxy, disclosedData: Data, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data)
  {
    report("entering handleSetContentForAllConnectionsFetch in StorePlatform", Severity.Trace)

    disclosedData match {
      case x: DisclosedData[ Data ] => {
        report("processSetContentRequest: found authorizedContent - connection type: " + x.connectionType)
        setContentByConnectionType(cnxn, parentRequestIds, parentRequestEventKey, newData, oldData, x)
      }
      case _ => {
      }
    }

    report("exiting handleSetContentForAllConnectionsFetch", Severity.Trace)
  }

  def setContentByConnectionTypeAndToTargetSelf(cnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data, disclosedData: DisclosedData[ Data ]) =
  {
    val fieldList = disclosedData.fields.split(',').toList
    val authorizedData = newData.authorizedData(fieldList)
    var oldAuthorizedData: Data = null
    if ( oldData != null ) {
      oldAuthorizedData = oldData.authorizedData(fieldList)
    }
    //   ### here do a second seach for all connection with policy "shared" -> take a self con -> use sesselfcon  to set data
    //get related connections for the target cnxn
    val connectionSearch = new Connection()
    connectionSearch.setConnectionType(disclosedData.connectionType)
    fetch[ Data ](_dbQ, cnxn, connectionSearch.toSearchKey, handleSetContentByConnectionTypeAndToTargetSelfFetch(_: AgentCnxnProxy, _: Data, parentRequestIds, parentRequestEventKey, authorizedData, oldAuthorizedData, disclosedData))
  }


  def setContentByConnectionType(cnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data, oldData: Data, disclosedData: DisclosedData[ Data ]) =
  {
    val fieldList = disclosedData.fields.split(',').toList
    val authorizedData = newData.authorizedData(fieldList)
    var oldAuthorizedData: Data = null
    if ( oldData != null ) {
      oldAuthorizedData = oldData.authorizedData(fieldList)
    }
    //   ### here do a second seach for all connection with policy "shared" -> take a self con -> use sesselfcon  to set data
    //get related connections for the target cnxn
    val connectionSearch = new Connection()
    connectionSearch.setConnectionType(disclosedData.connectionType)
    fetch[ Data ](_dbQ, cnxn, connectionSearch.toSearchKey, handleSetContentByConnectionTypeFetch(_: AgentCnxnProxy, _: Data, parentRequestIds, parentRequestEventKey, authorizedData, oldAuthorizedData, disclosedData))
  }

  def handleSetContentByConnectionTypeFetch(
    cnxn: AgentCnxnProxy,
    connection: Data,
    parentRequestIds: Identification,
    parentRequestEventKey: EventKey,
    authorizedData: Data,
    oldAuthorizedData: Data,
    authorizedContent: DisclosedData[ Data ])
  {
    report("entering handleSetContentByConnectionTypeFetch in StorePlatform", Severity.Trace)

    connection match {
      case x: Connection => {
        report("setContentByConnectionType: found connection: " + x.toString)
        updateDataById(x.writeCnxn, authorizedData)

        //        if ( x.policies != null && !x.policies.contains(ConnectionPolicy.RemoteSearchDisabled.toString) ) {
        updateCache(x.writeCnxn, x.readCnxn, parentRequestIds, parentRequestEventKey, authorizedData)
        //        }

        //we are now storing the authorizedContentAuditItem data on the connection junction as well
        //for audit logging purposes...

        val auditItem = authorizedContent.forAudit(x)
        //uncomment after we implement the approval process
        //auditItem.autoApproved = true
        //this is going to be a problem if the disclosure level changes
        //need update by search here
        updateDataById(x.writeCnxn, auditItem)
      }
      case _ => {
      }
    }

    report("exiting handleSetContentByConnectionTypeFetch in StorePlatform", Severity.Trace)
  }

  def handleSetContentByConnectionTypeAndToTargetSelfFetch(cnxn: AgentCnxnProxy,
    connection: Data,
    parentRequestIds: Identification,
    parentRequestEventKey: EventKey,
    authorizedData: Data,
    oldAuthorizedData: Data,
    authorizedContent: DisclosedData[ Data ])
  {
    report("entering handleSetContentByConnectionTypeAndToTargetSelfFetch in StorePlatform", Severity.Trace)

    handleSetContentByConnectionTypeFetch(cnxn, connection, parentRequestIds, parentRequestEventKey, authorizedData, oldAuthorizedData, authorizedContent)
    connection match {
      case x: Connection => {
        report("setContentByConnectionType: found connection: " + x.toString)
        // if DataSharingEnabled store in a self connection of the target connection the authorizedData
        if ( parentRequestIds != null && x.policies != null && !x.policies.isEmpty ) {
          if ( x.policies.contains(ConnectionPolicy.DataSharingEnabled.toString) ) {
            authorizedData match {
              case y: Redistributable => {
                // TODO logging
                // TODO eventually pass the really source of the request for logging or permission check
                // assumption that authorizedData and oldAuthorizedData has the same type or oldAuthorizedData ==null
                sendSetSelfContentRequest(parentRequestIds, parentRequestEventKey, x, authorizedData, oldAuthorizedData)
              }
              case _ => {
                // TODO logging
              }
            }

          }
        }
      }
      case _ => {
      }
    }

    report("exiting handleSetContentByConnectionTypeFetch in StorePlatform", Severity.Trace)
  }

  def updateCache(originCnxn: AgentCnxnProxy, targetCnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newData: Data): Unit = {
    //hook to implement in higher up libraries
    report("Cache not implemented", Severity.Trace)
  }


  def sendSetSelfContentRequest(parentRequestIds: Identification, parentRequestEventKey: EventKey, connectionToTarget: Connection, newContent: Data, oldContent: Data) =
  {
    val req = new SetSelfContentRequest(parentRequestIds.copyAsChild(), new EventKey(java.util.UUID.randomUUID, "") /*parentRequestEventKey*/ , newContent, oldContent)
    // hides the source of the request
    req.targetCnxn = connectionToTarget.readCnxn
    req.originCnxn = connectionToTarget.readCnxn
    send(_publicQ, connectionToTarget.readCnxn, req)
  }

  def setContentForSelfAndForCompositeConnections(selfCnxn: AgentCnxnProxy, parentRequestIds: Identification, parentRequestEventKey: EventKey, newCompositeData: CompositeData[ Data ], oldData: Data)
  {
    //store object on self Cnxn
    updateDataById(selfCnxn, newCompositeData.data)

    //TODO: make this search all conns
    updateDataById(newCompositeData.connection.writeCnxn, newCompositeData.data)

    if ( parentRequestIds != null && newCompositeData.connection.policies != null && !newCompositeData.connection.policies.isEmpty ) {

      if ( newCompositeData.connection.policies.contains(ConnectionPolicy.DataSharingEnabled.toString) ) {
        newCompositeData.data match {
          case y: Redistributable => {
            // TODO logging
            // TODO eventually pass the really source of the request for logging or permission check
            sendSetSelfContentRequest(parentRequestIds, parentRequestEventKey, newCompositeData.connection, newCompositeData.data, oldData)
          }
          case _ => {
            // TODO logging
          }
        }

      }
    }
  }

  def setContentIfConnectionChanged(msg: SetContentRequest) =
  {
    //if there is a new Connection or the Connection
    //TODO: change to (msg.newData, msg.oldData) match case (n: Connection, o: Connection)
    msg.newData match {
      case newConnection: Connection => {
        var oldConnection: Connection = null
        msg.oldData match {
          case x: Connection => {
            oldConnection = x
          }
          case _ => {
          } //don't throw an exception here as this could be a new connection?
        }
        processDisclosureDelta(msg.ids, msg.eventKey, newConnection, oldConnection, msg.targetCnxn)
        if ( oldConnection == null ) {
          processNewConnection(newConnection, msg.targetCnxn)
        }
      }
      case _ => {
      } //TODO: throw an exception
    }
  }


  def setContentIfDisclosedDataChanged(msg: SetContentRequest) =
  {
    (msg.newData, msg.oldData) match {
      case (x: DisclosedData[ _ ], y: DisclosedData[ _ ]) => {
        //println("OLD AND NEW DATA OF TYPE DISCLOSED DATA: " + x.getConnectionType() + "; on selfCnxn=" + msg.targetCnxn)

        val query = new Connection() //ConnectionFactory.createTypedConnection(x.getConnectionType())
        //  fetchList[ Connection ](_dbQ, msg.targetCnxn, query.toSearchKey, processConnectionsLookupForDiscloseDataUpdate(_: AgentCnxnProxy, _: List[ Connection ]))
        fetch[ Connection ](_dbQ, msg.targetCnxn, query.toSearchKey, processConnectionsLookupForDiscloseDataUpdate(_: AgentCnxnProxy, _: Connection, msg.ids, msg.eventKey, x, y))
      }
      case (x: DisclosedData[ _ ], _) => {
        // ignore this case
      }
      case _ => {
        // ignore this case
      }
    }
  }


  protected def processConnectionsLookupForDiscloseDataUpdate(selfCnxn: AgentCnxnProxy, connection: Connection, parentRequestIds: Identification, parentRequestEventKey: EventKey, newDisclosedData: DisclosedData[ Data ], oldDisclosedData: DisclosedData[ Data ]): Unit =
  {
   // println("$$$$$$$$$$$ ALL CONNECTIONS TO BE UPDATET selfCnxn=" + selfCnxn + " " + connection + "/n" + ",  newDisclosedData+" + newDisclosedData + ", oldDisclosedData=" + oldDisclosedData)

    changeDisclosedContentOnConnection(selfCnxn, parentRequestIds, parentRequestEventKey, connection, newDisclosedData, oldDisclosedData)

  }

  def processNewConnection(newConnection: Connection, selfCnxn: AgentCnxnProxy) =
  {
    //start listening
    if (isDistributedNetworkMode()) {
      listenPublicRequests(newConnection.writeCnxn)
      listenPublicResponses(newConnection.readCnxn)
    }

    //system Data generation
    generateSystemData(selfCnxn, newConnection)
    generateCacheData(selfCnxn, newConnection)
    //broker related functionality
    //    handleBrokerTaskForNewConnection(selfCnxn, newConnection)
  }


  def processDisclosureDelta( parentRequestIds: Identification, parentRequestEventKey: EventKey, newConnection: Connection, oldConnection: Connection, selfCnxn: AgentCnxnProxy)
  {
    if ( ( oldConnection == null ) || ( oldConnection != null && newConnection.connectionType != oldConnection.connectionType ) ) {
      //this should be transactional
      changeDisclosedContentOnConnection( parentRequestIds, parentRequestEventKey, newConnection, oldConnection, selfCnxn)
    }
  }

  protected def processSetContentAuthorizationRequest(msg: SetContentAuthorizationRequest)
  {
    val search = new AuthorizationRequest()
    search.setId(msg.authorizationReqForConnection.data.id.toString)
    fetch[ AuthorizationRequest ](_dbQ, msg.targetCnxn, search.toSearchKey, handleSetContentAuthorizationRequestFetch(_: AgentCnxnProxy, _: AuthorizationRequest, msg))
  }

  protected def handleSetContentAuthorizationRequestFetch(cnxn: AgentCnxnProxy, request: AuthorizationRequest, msg: SetContentAuthorizationRequest)
  {
    report("entering handleSetContentAuthorizationRequestFetch in StorePlatform", Severity.Trace)
    delete(_dbQ, msg.targetCnxn, request.toStoreKey)
    if ( msg.authorizationReqForConnection.data.approved.toBoolean ) {
      authorizeRequest(msg.authorizationReqForConnection.data.msg, true)
    }
    val response = new SetContentAuthorizationResponse(msg.ids.copyAsChild(), msg.eventKey.copy(), msg.authorizationReqForConnection)
    response.originCnxn = msg.originCnxn
    response.targetCnxn = msg.targetCnxn
    send(_publicQ, msg.originCnxn, response)
    report("exiting handleSetContentAuthorizationRequestFetch in StorePlatform", Severity.Trace)
  }

  protected def authorizeRequest(msg: GetContentRequest, manuallyApproved: Boolean)
  {
    val auditSearchItem = new AuthorizedContentAuditItem() // msg.queryObject)
    // TODO bug is here possible
    auditSearchItem.setObjectType(msg.queryObject.formattedClassName)
    fetch[ AuthorizedContentAuditItem ](_dbQ, msg.targetCnxn, auditSearchItem.toSearchKey, handleAuthorizeRequestAuthorizedContentAuditItem(_: AgentCnxnProxy, _: AuthorizedContentAuditItem, msg, manuallyApproved))
  }

  protected def handleAuthorizeRequestAuthorizedContentAuditItem(cnxn: AgentCnxnProxy, authorizedContentItemInstance: AuthorizedContentAuditItem, msg: GetContentRequest, manuallyApproved: Boolean)
  {
    report("entering handleAuthorizeRequestAuthorizedContentAuditItem in StorePlatform", Severity.Trace)
    if ( !manuallyApproved )
      logDataRequested(cnxn, authorizedContentItemInstance, msg)


    if ( authorizedContentItemInstance.autoApproved.toBoolean || manuallyApproved ) {
      logDataApproved(cnxn, authorizedContentItemInstance, msg)
      processGetContentRequest(msg)
    }
    else {
      persistAuthorizationRequest(msg)
      notifyUser(msg.targetCnxn, msg)
    }

  }

  protected def persistAuthorizationRequest(msg: GetContentRequest)
  {
    val objectName = msg.queryObject.className.trimPackage.fromCamelCase
    val authReq = new AuthorizationRequest(objectName, msg, new DateTime(), "false")
    var oldData: AuthorizationRequest = null
    updateDataById(msg.targetCnxn, authReq)
  }

  //public for test
  def processDeleteContentRequest(msg: DeleteContentRequest) =
  {
    report("entering processDeleteContentRequest in StorePlatform", Severity.Trace)
    fetch[ Data ](_dbQ, msg.targetCnxn, msg.queryObject.toSearchKey, deleteDataForSelf(_: AgentCnxnProxy, _: Data))

    val responseEventKey = if ( msg.eventKey == null ) null else msg.eventKey.copy()
    val response = new DeleteContentResponse(msg.ids.copyAsChild(), responseEventKey, msg.queryObject)
    response.originCnxn = msg.originCnxn
    response.targetCnxn = msg.targetCnxn
    report("Store sending msg on public queue cnxn:" + msg.originCnxn.toString)
    send(_publicQ, msg.originCnxn, response)

    report("exiting entering in StorePlatform", Severity.Trace)
  }

  protected def processSetContentPersistedRequest(msg: SetContentPersistedRequest) =
  {
    val search = new PersistedRequest()
    search.id = ( msg.persistedRequestData.data.id )
    fetch[ PersistedRequest ](_dbQ, msg.targetCnxn, search.toSearchKey, handleSetContentPersistedRequestFetch(_: AgentCnxnProxy, _: PersistedRequest, msg))

  }

  protected def handleSetContentPersistedRequestFetch(cnxn: AgentCnxnProxy, request: PersistedRequest, msg: SetContentPersistedRequest)
  {
    report("entering handleSetContentPersistedRequestFetch in StorePlatform", Severity.Trace)
    delete(_dbQ, msg.targetCnxn, request.toStoreKey)
    send(_publicQ, msg.response.targetCnxn, msg.response)

    val response = SetContentPersistedResponse(msg.ids.copyAsChild, msg.eventKey.copy(msg.eventKey.agentSessionId, msg.eventKey.eventTag), msg.persistedRequestData)
    response.originCnxn = msg.originCnxn
    response.targetCnxn = msg.targetCnxn
    send(_publicQ, msg.targetCnxn, response)
    report("exiting handleSetContentPersistedRequestFetch in StorePlatform", Severity.Trace)
  }

  protected def lookupConnection[ T <: Data ](queryObject: T, connection: Connection): AgentCnxnProxy =
  {
    //auditlog item is inverse of normal lookup because reading a connection causes data to be written on the read side
    queryObject match {
      case x: AuditLogItem => {
        //     case AuditLogItem => {
        connection.writeCnxn
      }
      case _ => {
        connection.readCnxn
      }
    }
  }

  protected def processSelfContentRequest(cnxnA_Broker: AgentCnxnProxy, setSelfContentRequest: SetSelfContentRequest) =
  {
    //get self cnxn from system data
    //lookup the self connection from the systemdata in the connection silo
    val queryObject = new SystemData(new Connection())
    fetch[ SystemData[ Connection ] ](_dbQ, cnxnA_Broker, queryObject.toSearchKey, handleSystemDataLookupSelfContentRequest(_: AgentCnxnProxy, _: SystemData[ Connection ], setSelfContentRequest))
  }


  protected def handleSystemDataLookupSelfContentRequest(cnxn: AgentCnxnProxy, systemConnection: SystemData[ Connection ], setSelfContentRequest: SetSelfContentRequest): Unit =
  {

    //save conn on self cnxn, send back response
    val setReq = new SetContentRequest(setSelfContentRequest.eventKey, setSelfContentRequest.newData, setSelfContentRequest.oldData)
    setReq.originCnxn = systemConnection.data.writeCnxn
    setReq.targetCnxn = systemConnection.data.writeCnxn
    setReq.channelLevel = Some(ChannelLevel.Public)
    report("ABOUT TO CREATE NEW CONNECTION: ", Severity.Info)
    send(_publicQ, setReq.targetCnxn, setReq)
  }

}