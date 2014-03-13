package org.projectmaxs.transport.xmpp.xmppservice;

/*
 This file is part of Project MAXS.

 MAXS and its modules is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 MAXS is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with MAXS.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.projectmaxs.shared.global.util.Log;
import org.projectmaxs.shared.maintransport.TransportConstants;
import org.projectmaxs.transport.xmpp.TransportService;

import android.content.Intent;

public class HandleConnectionListener extends StateChangeListener {

	private static final Log LOG = Log.getLog();

	private final XMPPService mXMPPService;

	private ConnectionListener mConnectionListener;

	public HandleConnectionListener(XMPPService xmppService) {
		mXMPPService = xmppService;
	}

	@Override
	public void connected(XMPPConnection connection) {
		mConnectionListener = new ConnectionListener() {

			@Override
			public void connectionClosed() {}

			@Override
			public void connectionClosedOnError(Exception arg0) {
				LOG.w("connectionClosedOnError");
				mXMPPService.disconnect();
				// We don't call scheduleReconnect() here, because this method is usually be called
				// from Smack's PacketReader or PacketWriter thread, which will not have a Looper
				// (and shouldn't get one) and therefore is unable to use the reconnect handler.
				// Instead we send an START_SERVICE intent for the transport service
				Intent intent = new Intent(mXMPPService.getContext(), TransportService.class);
				intent.setAction(TransportConstants.ACTION_START_SERVICE);
				mXMPPService.getContext().startService(intent);
			}

			@Override
			public void reconnectingIn(int arg0) {
				throw new IllegalStateException("Reconnection Manager is running");
			}

			@Override
			public void reconnectionFailed(Exception arg0) {
				throw new IllegalStateException("Reconnection Manager is running");
			}

			@Override
			public void reconnectionSuccessful() {
				throw new IllegalStateException("Reconnection Manager is running");
			}

		};
		connection.addConnectionListener(mConnectionListener);
	}

	@Override
	public void disconnected(XMPPConnection connection) {
		connection.removeConnectionListener(mConnectionListener);
		mConnectionListener = null;
	}

}
