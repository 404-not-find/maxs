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

package org.projectmaxs.main.xmpp;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.MultipleRecipientManager;
import org.projectmaxs.main.MAXSService;
import org.projectmaxs.main.Settings;
import org.projectmaxs.main.StateChangeListener;
import org.projectmaxs.shared.util.Log;

public class XMPPService {
	private static Log sLog = Log.getLog();

	private Set<StateChangeListener> mStateChangeListeners = new HashSet<StateChangeListener>();
	private State mState = State.Disconnected;
	private Settings mSettings;
	private ConnectionConfiguration mConnectionConfiguration;
	private XMPPConnection mConnection;
	private MAXSService mMAXSLocalService;

	/**
	 * Creates a new XMPPService instance. The XMPP connection will be
	 * automatically resumed if it was previously established
	 * 
	 * @param maxsLocalService
	 */
	public XMPPService(MAXSService maxsLocalService) {
		SmackAndroid.init(maxsLocalService);

		mSettings = Settings.getInstance(maxsLocalService);
		mMAXSLocalService = maxsLocalService;

		addListener(new HandleChatPacketListener(this, mSettings));
		addListener(new HandleConnectionListener(this, mSettings));
		addListener(new XMPPRoster(mSettings));
	}

	public enum State {
		Connected, Connecting, Disconnecting, Disconnected, WaitingForNetwork, WaitingForRetry;
	}

	public State getCurrentState() {
		return mState;
	}

	public boolean isConnected() {
		return (getCurrentState() == State.Connected);
	}

	public void addListener(StateChangeListener listener) {
		mStateChangeListeners.add(listener);
	}

	public void removeListener(StateChangeListener listener) {
		mStateChangeListeners.remove(listener);
	}

	public void connect() {
		changeState(XMPPService.State.Connected);
	}

	public void disconnect() {
		changeState(XMPPService.State.Disconnected);
	}

	public void reconnect() {
		disconnect();
		connect();
	}

	public void send(org.projectmaxs.shared.Message message, String originIssuerInfo) {
		if (mConnection == null || !mConnection.isAuthenticated()) {
			// TODO add to DB
			sLog.d("foo");
			return;
		}

		String to = originIssuerInfo;
		Message packet = new Message();
		packet.setType(Message.Type.chat);
		packet.setBody(message.getRawContent());

		if (to == null) {
			List<String> toList = new LinkedList<String>();
			for (String masterJid : mSettings.getMasterJids()) {
				Iterator<Presence> presences = mConnection.getRoster().getPresences(masterJid);
				while (presences.hasNext()) {
					Presence p = presences.next();
					String fullJID = p.getFrom();
					String resource = StringUtils.parseResource(fullJID);
					// Don't send messages to GTalk Android devices
					// It would be nice if there was a better way to detect
					// an Android gTalk XMPP client, but currently there is none
					if (resource != null && !resource.equals("") && (!resource.startsWith("android"))) {
						toList.add(fullJID);
					}
				}
			}
			try {
				MultipleRecipientManager.send(mConnection, packet, toList, null, null);
			} catch (XMPPException e) {
				sLog.w("MultipleRecipientManager exception", e);
				return;
			}
		}
		else {
			packet.setTo(to);
			mConnection.sendPacket(packet);
		}

		return;
	}

	public void newConnecitivytInformation(boolean connected, boolean networkTypeChanged) {
		// first disconnect if the network type changed and we are now connected
		// with an now unusable network
		if (networkTypeChanged && isConnected()) {
			disconnect();
		}

		// if we have an connected network but we are not connected, connect
		if (connected && !isConnected()) {
			connect();
		}
	}

	protected void newMessageFromMasterJID(Message message) {
		String body = message.getBody();
		if (body == null) return;

		String[] splitedBody = body.split(" ");
		String command = splitedBody[0];

		String subCmd = null;
		if (splitedBody.length > 1) subCmd = splitedBody[1];

		String args = null;
		if (splitedBody.length > 2) {
			StringBuilder sb = new StringBuilder();
			for (int i = 2; i < splitedBody.length; i++)
				sb.append(splitedBody[i]);
			args = sb.toString();
		}
		String from = message.getFrom();
		mMAXSLocalService.performCommand(command, subCmd, args, MAXSService.CommandOrigin.XMPP, null, from);
	}

	private void newState(State newState) {
		for (StateChangeListener l : mStateChangeListeners) {
			switch (newState) {
			case Connected:
				l.connected(mConnection);
				break;
			case Disconnected:
				l.disconnected(mConnection);
				break;
			case Connecting:
				l.connecting();
				break;
			case Disconnecting:
				l.disconnecting();
				break;
			default:
				break;
			}
		}
		switch (newState) {
		case Connected:
		case Connecting:
		case WaitingForNetwork:
		case WaitingForRetry:
			mSettings.setXMPPConnectionState(true);
			break;
		case Disconnected:
		case Disconnecting:
			mSettings.setXMPPConnectionState(false);
		default:
			break;
		}
		mState = newState;
	}

	private synchronized void changeState(State newState) {
		sLog.d("changeState(): mState=" + mState + ", newState=" + newState);
		switch (mState) {
		case Connected:
			switch (newState) {
			case Connected:
				break;
			case Disconnected:
				disconnectConnection();
				break;
			case WaitingForNetwork:
				disconnectConnection();
				newState(State.WaitingForNetwork);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		case Disconnected:
			switch (newState) {
			case Disconnected:
				break;
			case Connected:
				tryToConnect();
				break;
			case WaitingForNetwork:
				newState(newState);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		case WaitingForNetwork:
			switch (newState) {
			case WaitingForNetwork:
				break;
			case Connected:
				tryToConnect();
				break;
			case Disconnected:
				newState(State.Disconnected);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		case WaitingForRetry:
			switch (newState) {
			case WaitingForNetwork:
				break;
			case Connected:
				newState(State.Connected);
				break;
			case Disconnected:
				newState(newState);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		default:
			sLog.w("changeState(): Unkown state change combination. mState=" + mState + ", newState=" + newState);
			// TODO enable this
			// throw new IllegalStateException();
		}
	}

	private void tryToConnect() {
		sLog.d("tryToConnect()");
		newState(State.Connecting);

		XMPPConnection con;
		boolean newConnection = false;

		try {
			if (mConnectionConfiguration == null || mConnectionConfiguration != mSettings.getConnectionConfiguration()) {
				con = new XMPPConnection(mSettings.getConnectionConfiguration());
				newConnection = true;
			}
			else {
				con = mConnection;
			}
		} catch (XMPPException e) {
			sLog.e("tryToConnect() connection configuration failed", e);
			// TODO try reconnect
			return;
		}

		try {
			con.connect();
		} catch (XMPPException e) {
			sLog.e("Exception from connect()", e);
			return;
		}

		if (!con.isAuthenticated()) {
			try {
				con.login(mSettings.getJid(), mSettings.getPassword(), "MAXS");
			} catch (XMPPException e) {
				sLog.e("tryToConnect() login failed", e);
				return;
			}
		}

		// Login Successful

		mConnection = con;

		if (newConnection) {
			for (StateChangeListener l : mStateChangeListeners) {
				l.newConnection(mConnection);
			}
		}

		// TODO handle offline messages as StateChangeListener
		// TODO ping failed listener as StateChangeListener

		sLog.d("tryToConnect() successfully connected");
		newState(State.Connected);

		// Send the first presence *after* all StateChangeListeners have been
		// called, in order to do their work prior the user's firsts knowledge
		// of the online/available state
		mConnection.sendPacket(new Presence(Presence.Type.available));
	}

	private void disconnectConnection() {
		if (mConnection != null) {
			if (mConnection.isConnected()) {
				newState(State.Disconnecting);
				// TODO better disconnect handle (e.g. in extra thread)
				mConnection.disconnect();
			}
			newState(State.Disconnected);
		}
	}

}
