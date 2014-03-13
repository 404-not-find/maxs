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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.projectmaxs.transport.xmpp.Settings;

public class XMPPRoster extends StateChangeListener implements RosterListener {

	private final List<MasterJidListener> mMasterJidListeners = new LinkedList<MasterJidListener>();

	private Settings mSettings;
	private Roster mRoster;
	private XMPPConnection mConnection;
	private boolean mMasterJidAvailable;

	public XMPPRoster(Settings settings) {
		mSettings = settings;
	}

	/*
	 * StateChangeListener callbacks
	 */

	@Override
	public void newConnection(XMPPConnection connection) throws XMPPException, SmackException {
		mConnection = connection;
		mRoster = connection.getRoster();
		mRoster.addRosterListener(this);
	}

	@Override
	public void connected(XMPPConnection connection) {
		Set<String> masterJids = mSettings.getMasterJids();
		for (String jid : masterJids)
			friendJid(jid);

		checkMasterJids();
	}

	@Override
	public void disconnected(XMPPConnection connection) {
		mMasterJidAvailable = false;
	}

	/*
	 * RosterListener callbacks
	 */

	@Override
	public void entriesAdded(Collection<String> arg0) {}

	@Override
	public void entriesDeleted(Collection<String> arg0) {}

	@Override
	public void entriesUpdated(Collection<String> arg0) {}

	@Override
	public void presenceChanged(Presence presence) {
		checkMasterJids();
	}

	protected boolean isMasterJidAvailable() {
		return mMasterJidAvailable;
	}

	protected void addMasterJidListener(MasterJidListener listener) {
		mMasterJidListeners.add(listener);
	}

	protected void removeMasterJidListener(MasterJidListener listener) {
		mMasterJidListeners.remove(listener);
	}

	private void checkMasterJids() {
		boolean masterJidAvailable = false;
		for (String jid : mSettings.getMasterJids()) {
			Presence presence = mRoster.getPresence(jid);
			if (presence.isAvailable()) {
				masterJidAvailable = true;
				// we found at least one available master JID, break here
				break;
			}
		}

		if (mMasterJidAvailable == false && masterJidAvailable == true) {
			for (MasterJidListener listener : mMasterJidListeners)
				listener.masterJidAvailable();
		}

		mMasterJidAvailable = masterJidAvailable;
	}

	/**
	 * Subscribe and request subscription with a given JID. Essentially become a
	 * "friend" of the JID.
	 * 
	 * @param userID
	 */
	private void friendJid(String userID) {
		if (!mRoster.contains(userID)) {
			try {
				mRoster.createEntry(userID, StringUtils.parseBareAddress(userID), null);
				grantSubscription(userID, mConnection);
				requestSubscription(userID, mConnection);
			} catch (Exception e) {
				// TODO
				return;
			}
		}
		RosterEntry rosterEntry = mRoster.getEntry(userID);
		// async code here, the server may not have added the entry yet, so bail
		// out here
		if (rosterEntry == null) return;

		RosterPacket.ItemType type = rosterEntry.getType();
		switch (type) {
		case from:
			requestSubscription(userID, mConnection);
			break;
		case to:
			grantSubscription(userID, mConnection);
			break;
		case none:
			grantSubscription(userID, mConnection);
			requestSubscription(userID, mConnection);
			break;
		case both:
		default:
			break;
		}
	}

	/**
	 * grants the given JID the subscription (e.g. viewing your online state)
	 * 
	 * @param jid
	 * @param connection
	 */
	private static void grantSubscription(String jid, XMPPConnection connection) {
		Presence presence = new Presence(Presence.Type.subscribed);
		sendPresenceTo(jid, presence, connection);
	}

	/**
	 * request the subscription from a given JID
	 * 
	 * @param jid
	 * @param connection
	 */
	private static void requestSubscription(String jid, XMPPConnection connection) {
		Presence presence = new Presence(Presence.Type.subscribe);
		sendPresenceTo(jid, presence, connection);
	}

	private static void sendPresenceTo(String to, Presence presence, XMPPConnection connection) {
		presence.setTo(to);
		connection.sendPacket(presence);
	}

	public static class MasterJidListener {
		public void masterJidAvailable() {}
	}
}
