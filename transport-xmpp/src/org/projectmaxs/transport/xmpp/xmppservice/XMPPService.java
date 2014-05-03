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

package org.projectmaxs.transport.xmpp.xmppservice;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.ConnectionException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPTCPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.dns.HostAddress;
import org.jivesoftware.smackx.address.MultipleRecipientManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.projectmaxs.shared.global.GlobalConstants;
import org.projectmaxs.shared.global.util.Log;
import org.projectmaxs.shared.maintransport.CommandOrigin;
import org.projectmaxs.shared.maintransport.TransportConstants;
import org.projectmaxs.shared.transport.transform.TransformMessageContent;
import org.projectmaxs.transport.xmpp.Settings;
import org.projectmaxs.transport.xmpp.database.MessagesTable;
import org.projectmaxs.transport.xmpp.util.ConnectivityManagerUtil;
import org.projectmaxs.transport.xmpp.util.Constants;
import org.projectmaxs.transport.xmpp.util.XHTMLIMUtil;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class XMPPService {
	private static final Log LOG = Log.getLog();

	private static XMPPService sXMPPService;

	private final Set<StateChangeListener> mStateChangeListeners = Collections
			.synchronizedSet(new HashSet<StateChangeListener>());

	private final Settings mSettings;
	private final MessagesTable mMessagesTable;
	private final Context mContext;
	private final HandleTransportStatus mHandleTransportStatus;

	private XMPPStatus mXMPPStatus;
	private State mState = State.Disconnected;

	static {
		ServiceDiscoveryManager.setDefaultIdentity(new DiscoverInfo.Identity("client",
				GlobalConstants.NAME, "bot"));
		// TODO This is not really needed, but for some reason the static initializer block of
		// LastActivityManager is not run. This could be a problem caused by aSmack together with
		// dalvik, as the initializer is run on Smack's test cases.
		LastActivityManager.setEnabledPerDefault(true);
		// Some network types, especially GPRS or EDGE is rural areas have a very slow response
		// time. Smack's default packet reply timeout of 5 seconds is way to low for such networks,
		// so we increase it to 2 minutes.
		SmackConfiguration.setDefaultPacketReplyTimeout(2 * 60 * 1000);
	}

	private final Runnable mReconnectRunnable = new Runnable() {
		@Override
		public void run() {
			LOG.d("scheduleReconnect: calling tryToConnect");
			tryToConnect();
		}
	};

	/**
	 * Switch boolean to ensure that the disconnected(XMPPConnection) listeners are
	 * only run if there was a previous connected connection.
	 */
	private boolean mConnected = false;

	private ConnectionConfiguration mConnectionConfiguration;
	private XMPPConnection mConnection;
	private Handler mReconnectHandler;

	/**
	 * Get an XMPPService
	 * 
	 * Note that because of MemorizingTrustManager Context must be an instance of Application,
	 * Service or Activity. Therefore if you have an Context which is not Service or Activity, use
	 * getApplication().
	 * 
	 * @param context
	 *            as an instance of Application, Service or Activity.
	 * @return The XMPPService instance.
	 */
	public static synchronized XMPPService getInstance(Context context) {
		if (sXMPPService == null) sXMPPService = new XMPPService(context);
		return sXMPPService;
	}

	private XMPPService(Context context) {
		XMPPEntityCapsCache.initialize(context);

		mContext = context;
		mSettings = Settings.getInstance(context);
		mMessagesTable = MessagesTable.getInstance(context);

		addListener(new HandleChatPacketListener(this));
		addListener(new HandleConnectionListener(this));
		addListener(new HandleMessagesListener(this));
		addListener(new XMPPPingManager(this));
		addListener(new XMPPFileTransfer(context));
		addListener(new XMPPDeliveryReceipts());
		addListener(new XMPPPrivacyList(mSettings));

		mHandleTransportStatus = new HandleTransportStatus(context);
		addListener(mHandleTransportStatus);
		XMPPRoster xmppRoster = new XMPPRoster(mSettings);
		addListener(xmppRoster);
		mXMPPStatus = new XMPPStatus(xmppRoster, context);
		addListener(mXMPPStatus);
	}

	public static enum State {
		Connected, Connecting, Disconnecting, Disconnected, WaitingForNetwork, WaitingForRetry;
	}

	public State getCurrentState() {
		return mState;
	}

	public boolean isConnected() {
		return (getCurrentState() == State.Connected);
	}

	public HandleTransportStatus getHandleTransportStatus() {
		return mHandleTransportStatus;
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

	public void setStatus(String status) {
		mXMPPStatus.setStatus(status);
	}

	public void newConnecitivytInformation(boolean connected, boolean networkTypeChanged) {
		// first disconnect if the network type changed and we are now connected
		// with an now unusable network
		if ((networkTypeChanged && isConnected()) || !connected) {
			LOG.d("newConnectivityInformation: calling disconnect() networkTypeChanged="
					+ networkTypeChanged + " connected=" + connected + " isConnected="
					+ isConnected());
			disconnect();
		}

		// if we have an connected network but we are not connected, connect
		if (connected && !isConnected()) {
			LOG.d("newConnectivityInformation: calling connect()");
			connect();
		} else if (!connected) {
			LOG.d("newConnectivityInformation: we are not connected any more, changing state to WaitingForNetwork");
			newState(State.WaitingForNetwork);
		}
	}

	public void send(org.projectmaxs.shared.global.Message message, CommandOrigin origin) {
		// If the origin is null, then we are receiving a broadcast message from
		// main. TODO document that origin can be null
		if (origin == null) {
			sendAsMessage(message, null, null);
			return;
		}

		String action = origin.getIntentAction();
		String originId = origin.getOriginId();
		String originIssuerInfo = origin.getOriginIssuerInfo();

		if (Constants.ACTION_SEND_AS_MESSAGE.equals(action)) {
			sendAsMessage(message, originIssuerInfo, originId);
		} else if (Constants.ACTION_SEND_AS_IQ.equals(action)) {
			sendAsIQ(message, originIssuerInfo, originId);
		} else {
			throw new IllegalStateException("XMPPService send: unknown action=" + action);
		}
	}

	public XMPPConnection getConnection() {
		return mConnection;
	}

	Context getContext() {
		return mContext;
	}

	private void sendAsMessage(org.projectmaxs.shared.global.Message message,
			String originIssuerInfo, String originId) {
		if (mConnection == null || !mConnection.isAuthenticated()) {
			// TODO I think that this could for example happen when the service
			// is not started but e.g. the SMS receiver get's a new message.
			LOG.i("sendAsMessage: Not connected, adding message to DB. mConnection=" + mConnection);
			mMessagesTable.addMessage(message, Constants.ACTION_SEND_AS_MESSAGE, originIssuerInfo,
					originId);
			return;
		}

		String to = originIssuerInfo;
		Message packet = new Message();
		packet.setType(Message.Type.chat);
		packet.setBody(TransformMessageContent.toString(message));
		packet.setThread(originId);

		List<String> toList = new LinkedList<String>();
		if (to == null) {
			// Broadcast to all masterJID resources
			for (String masterJid : mSettings.getMasterJids()) {
				Collection<Presence> presences = mConnection.getRoster().getPresences(masterJid);
				for (Presence p : presences) {
					String fullJID = p.getFrom();
					String resource = StringUtils.parseResource(fullJID);
					if (!mSettings.isExcludedResource(resource)) {
						toList.add(fullJID);
					}
				}
			}

			// (a)Smacks getRoster() is a little bit cranky at the moment. Besides everything XMPP
			// related being asynchronous, aSmacks getRoster is known to be often empty when the
			// method is called shortly after the login. We put some effort into the issue, but
			// until this is fixed, we have to deal with the situation that toList may be empty
			// sometimes. But since a broadcast should get delivered to every master JID, it is
			// not really a problem.
			for (String jid : mSettings.getMasterJids()) {
				boolean found = false;
				for (String toJid : toList) {
					if (StringUtils.parseBareAddress(toJid).equals(jid)) {
						found = true;
						break;
					}
				}
				// Add this master JID, if it isn't already contained in toList
				if (!found) toList.add(jid);
			}
		} else {
			toList.add(to);
		}

		boolean atLeastOneSupportsXHTMLIM = false;
		for (String jid : toList) {
			try {
				atLeastOneSupportsXHTMLIM = XHTMLManager.isServiceEnabled(mConnection, jid);
			} catch (Exception e) {
				atLeastOneSupportsXHTMLIM = false;
			}
			if (atLeastOneSupportsXHTMLIM) break;
		}
		if (atLeastOneSupportsXHTMLIM)
			XHTMLIMUtil.addXHTMLIM(packet, TransformMessageContent.toFormatedText(message));

		try {
			MultipleRecipientManager.send(mConnection, packet, toList, null, null);
		} catch (Exception e) {
			LOG.e("sendAsMessage: Got Exception, adding message to DB");
			mMessagesTable.addMessage(message, Constants.ACTION_SEND_AS_MESSAGE, originIssuerInfo,
					originId);
		}
	}

	private void sendAsIQ(org.projectmaxs.shared.global.Message message, String originIssuerInfo,
			String issuerId) {
		// in a not so far future
	}

	protected void newMessageFromMasterJID(Message message) {
		String command = message.getBody();
		if (command == null) {
			LOG.e("newMessageFromMasterJID: empty body");
			return;
		}

		String issuerInfo = message.getFrom();
		LOG.d("newMessageFromMasterJID: command=" + command + " from=" + issuerInfo);

		Intent intent = new Intent(GlobalConstants.ACTION_PERFORM_COMMAND);
		CommandOrigin origin = new CommandOrigin(Constants.PACKAGE,
				Constants.ACTION_SEND_AS_MESSAGE, issuerInfo, null);
		intent.putExtra(TransportConstants.EXTRA_COMMAND, command);
		intent.putExtra(TransportConstants.EXTRA_COMMAND_ORIGIN, origin);
		intent.setClassName(GlobalConstants.MAIN_PACKAGE, TransportConstants.MAIN_TRANSPORT_SERVICE);
		ComponentName cn = mContext.startService(intent);
		if (cn == null) {
			LOG.e("newMessageFromMasterJID: could not start main transport service");
		}
	}

	protected void scheduleReconnect() {
		newState(State.WaitingForRetry);
		if (mReconnectHandler == null) mReconnectHandler = new Handler();
		mReconnectHandler.removeCallbacks(mReconnectRunnable);
		LOG.d("scheduleReconnect: scheduling reconnect in 10 seconds");
		mReconnectHandler.postDelayed(mReconnectRunnable, 10000);
	}

	private void newState(State newState) {
		newState(newState, "");
	}

	/**
	 * Notifies the StateChangeListeners about the new state and sets mState to
	 * newState. Does not add a log message.
	 * 
	 * @param newState
	 * @param reason
	 *            the reason for the new state (only used is newState is Disconnected)
	 */
	private void newState(State newState, String reason) {
		if (reason == null) reason = "";
		switch (newState) {
		case Connected:
			for (StateChangeListener l : mStateChangeListeners) {
				try {
					l.connected(mConnection);
				} catch (NotConnectedException e) {
					LOG.w("newState", e);
					// Do not call 'changeState(State.Disconnected)' here, instead simply schedule
					// reconnect since we obviously didn't reach the connected state. Changing the
					// state to Disconnected will create a transition from 'Connecting' to
					// 'Disconnected', which why avoid implementing here
					scheduleReconnect();
					return;
				}
			}
			mConnected = true;
			break;
		case Disconnected:
			for (StateChangeListener l : mStateChangeListeners) {
				l.disconnected(reason);
				if (mConnection != null && mConnected) l.disconnected(mConnection);
			}
			mConnected = false;
			break;
		case Connecting:
			for (StateChangeListener l : mStateChangeListeners)
				l.connecting();
			break;
		case Disconnecting:
			for (StateChangeListener l : mStateChangeListeners)
				l.disconnecting();
			break;
		case WaitingForNetwork:
			for (StateChangeListener l : mStateChangeListeners)
				l.waitingForNetwork();
			break;
		case WaitingForRetry:
			for (StateChangeListener l : mStateChangeListeners)
				l.waitingForRetry();
			break;
		default:
			break;
		}
		mState = newState;
	}

	private synchronized void changeState(State desiredState) {
		LOG.d("changeState: mState=" + mState + ", desiredState=" + desiredState);
		switch (mState) {
		case Connected:
			switch (desiredState) {
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
			switch (desiredState) {
			case Disconnected:
				break;
			case Connected:
				tryToConnect();
				break;
			case WaitingForNetwork:
				newState(State.WaitingForNetwork);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		case WaitingForNetwork:
			switch (desiredState) {
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
			switch (desiredState) {
			case WaitingForNetwork:
				newState(State.WaitingForNetwork);
				break;
			case Connected:
				// Do nothing here, instead, wait until the reconnect runnable did it's job.
				// Otherwise deadlocks may occur, because the connection attempts will block the
				// main thread, which will prevent SmackAndroid from receiving the
				// ConnecvitvityChange receiver and calling Resolver.refresh(). So we have no
				// up-to-date DNS server information, which will cause connect to fail.
				break;
			case Disconnected:
				newState(State.Disconnected);
				mReconnectHandler.removeCallbacks(mReconnectRunnable);
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		default:
			throw new IllegalStateException("changeState: Unkown state change combination. mState="
					+ mState + ", desiredState=" + desiredState);
		}
	}

	private synchronized void tryToConnect() {
		String failureReason = mSettings.checkIfReadyToConnect();
		if (failureReason != null) {
			LOG.w("tryToConnect: failureReason=" + failureReason);
			mHandleTransportStatus.setAndSendStatus("Unable to connect: " + failureReason);
			return;
		}

		if (isConnected()) {
			LOG.d("tryToConnect: already connected, nothing to do here");
			return;
		}
		if (!ConnectivityManagerUtil.hasDataConnection(mContext)) {
			LOG.d("tryToConnect: no data connection available");
			newState(State.WaitingForNetwork);
			return;
		}

		newState(State.Connecting);

		XMPPConnection connection;
		boolean newConnection = false;

		if (mConnectionConfiguration == null || mConnectionConfiguration != mSettings
		// We need to use an Application context instance here, because some Contexts may not work.
				.getConnectionConfiguration(mContext)) {
			connection = new XMPPTCPConnection(mSettings.getConnectionConfiguration(mContext));
			newConnection = true;
		} else {
			connection = mConnection;
		}

		LOG.d("tryToConnect: connect");
		try {
			connection.connect();
		} catch (Exception e) {
			LOG.e("tryToConnect: Exception from connect()", e);
			if (e instanceof ConnectionException) {
				ConnectionException ce = (ConnectionException) e;
				String error = "The following host's failed to connect to:";
				for (HostAddress ha : ce.getFailedAddresses())
					error += " " + ha;
				LOG.d("tryToConnect: " + error);
			}
			scheduleReconnect();
			return;
		}

		if (!connection.isAuthenticated()) {
			try {
				connection.login(StringUtils.parseName(mSettings.getJid()),
						mSettings.getPassword(), "MAXS");
			} catch (NoResponseException e) {
				LOG.w("tryToConnect: NoResponseException. Scheduling reconnect.");
				scheduleReconnect();
				return;
			} catch (Exception e) {
				LOG.e("tryToConnect: login failed. New State: Disconnected", e);
				newState(State.Disconnected, e.getLocalizedMessage());
				return;
			}
		}
		// Login Successful

		mConnection = connection;

		if (newConnection) {
			for (StateChangeListener l : mStateChangeListeners) {
				l.newConnection(mConnection);
			}
		}

		newState(State.Connected);

		LOG.d("tryToConnect: successfully connected \\o/");
	}

	private synchronized void disconnectConnection() {
		if (mConnection != null) {
			if (mConnection.isConnected()) {
				newState(State.Disconnecting);
				LOG.d("disconnectConnection: disconnect start");
				try {
					mConnection.disconnect();
				} catch (NotConnectedException e) {
					LOG.i("disconnectConnection", e);
				}
				LOG.d("disconnectConnection: disconnect stop");
			}
			newState(State.Disconnected);
		}
	}

}
