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

package org.projectmaxs.transport.xmpp;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;

public class XMPPPingManager extends StateChangeListener implements PingFailedListener {

	private static final Log LOG = Log.getLog();

	private final XMPPService mXMPPService;

	protected XMPPPingManager(XMPPService service) {
		mXMPPService = service;
	}

	public void connected(Connection connection) {
		PingManager.getInstanceFor(connection).registerPingFailedListener(this);
	}

	public void disconnected(Connection connection) {
		PingManager.getInstanceFor(connection).unregisterPingFailedListener(this);
	}

	@Override
	public void pingFailed() {
		LOG.w("ping failed: issuing reconnect");
		mXMPPService.reconnect();
	}

}
