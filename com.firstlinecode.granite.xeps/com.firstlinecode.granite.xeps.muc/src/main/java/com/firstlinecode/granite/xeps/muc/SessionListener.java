package com.firstlinecode.granite.xeps.muc;

import java.util.Map;
import java.util.Map.Entry;

import com.firstlinecode.basalt.protocol.core.JabberId;
import com.firstlinecode.granite.framework.core.annotations.Dependency;
import com.firstlinecode.granite.framework.core.connection.IConnectionContext;
import com.firstlinecode.granite.framework.core.session.ISessionListener;
import com.firstlinecode.granite.framework.processing.IProcessingContext;

public class SessionListener implements ISessionListener {
	@Dependency("muc.protocols.processor")
	private MucProtocolsProcessor mucProtocolsProcessor;
	
	@Override
	public void sessionEstablished(IConnectionContext context, JabberId sessionJid) {}

	@Override
	public void sessionClosing(IConnectionContext context, JabberId sessionJid) {
		Map<JabberId, String> roomJidAndNickMap = MucSessionUtils.getOrCreateRoomJidAndNickMap(context);
		for (Entry<JabberId, String> roomJidAndNick : roomJidAndNickMap.entrySet()) {
			mucProtocolsProcessor.exitRoom((IProcessingContext)context, roomJidAndNick.getKey(), roomJidAndNick.getValue());
		}
	}

	@Override
	public void sessionClosed(IConnectionContext context, JabberId sessionJid) {}

	@Override
	public void sessionEstablishing(IConnectionContext context, JabberId sessionJid) {}

}
