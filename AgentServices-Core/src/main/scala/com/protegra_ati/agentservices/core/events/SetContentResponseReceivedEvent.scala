/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.protegra_ati.agentservices.core.events

import com.protegra_ati.agentservices.core.messages.content._

class SetContentResponseReceivedEvent(source:SetContentResponse) extends MessageEvent[SetContentResponse](source) {

  override def trigger(listeners:List[MessageEventAdapter]){
    listeners.map {x => x.setContentResponseReceived(this)}
  }
  
}