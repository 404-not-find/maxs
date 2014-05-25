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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map.Entry;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.cache.EntityCapsPersistentCache;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.provider.DiscoverInfoProvider;
import org.projectmaxs.shared.global.GlobalConstants;
import org.projectmaxs.shared.global.util.Log;
import org.projectmaxs.transport.xmpp.database.XMPPEntityCapsTable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class XMPPEntityCapsCache implements EntityCapsPersistentCache {

	private final static Log LOG = Log.getLog();

	static {
		EntityCapsManager.setDefaultEntityNode(GlobalConstants.HOMEPAGE_URL);
		// We assume the number of XMPP entities the MAXS account is able to retrieve the presence
		// from is not big and therefore we limit the Cache size to 50
		EntityCapsManager.setCapsMaxCacheSize(50);
	}

	private static XMPPEntityCapsCache sXMPPEntityCapsCache;

	public static void initialize(Context context) {
		if (sXMPPEntityCapsCache != null) return;
		sXMPPEntityCapsCache = new XMPPEntityCapsCache(context);
	}

	private XMPPEntityCapsTable mXMPPEntityCapsTable;

	private XMPPEntityCapsCache(Context context) {
		mXMPPEntityCapsTable = XMPPEntityCapsTable.getInstance(context);
		try {
			EntityCapsManager.setPersistentCache(this);
		} catch (IOException e) {
			LOG.w("setPersistentCache", e);
		}
		context.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				LOG.i("ACTION_DEVICE_STORAGE_LOW received, emptying EntityCapsCache");
				emptyCache();
			}
		}, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
	}

	@Override
	public void addDiscoverInfoByNodePersistent(String node, DiscoverInfo info) {
		mXMPPEntityCapsTable.addDiscoverInfo(node, info.toXML());
	}

	@Override
	public void emptyCache() {
		mXMPPEntityCapsTable.emptyTable();
	}

	@Override
	public void replay() throws IOException {
		Iterator<Entry<String, String>> it = mXMPPEntityCapsTable.getDiscoverInfos().entrySet()
				.iterator();
		while (it.hasNext()) {
			Entry<String, String> entry = it.next();
			String node = entry.getKey();
			DiscoverInfo info = discoverInfoFromString(entry.getValue());
			if (info == null) continue;
			EntityCapsManager.addDiscoverInfoByNode(node, info);
		}
	}

	// TODO this code should be provided by smack instead
	// I guess what Smack needs is an convenient way to transform Strings into
	// all kinds of Packets.
	private DiscoverInfo discoverInfoFromString(String string) {
		Reader reader = new StringReader(string);
		String id;
		String from;
		String to;
		XmlPullParser parser;
		try {
			parser = XmlPullParserFactory.newInstance().newPullParser();
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			parser.setInput(reader);
		} catch (XmlPullParserException xppe) {
			xppe.printStackTrace();
			return null;
		}

		DiscoverInfo iqPacket;
		IQProvider provider = new DiscoverInfoProvider();

		// Parse the IQ, we only need the id
		try {
			parser.next();
			id = parser.getAttributeValue("", "id");
			from = parser.getAttributeValue("", "from");
			to = parser.getAttributeValue("", "to");
			parser.next();
		} catch (XmlPullParserException e1) {
			return null;
		} catch (IOException e) {
			return null;
		}

		try {
			iqPacket = (DiscoverInfo) provider.parseIQ(parser);
		} catch (Exception e) {
			return null;
		}

		iqPacket.setPacketID(id);
		iqPacket.setFrom(from);
		iqPacket.setTo(to);
		iqPacket.setType(IQ.Type.RESULT);
		return iqPacket;
	}
}
