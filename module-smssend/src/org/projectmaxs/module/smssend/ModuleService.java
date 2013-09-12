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

package org.projectmaxs.module.smssend;

import java.util.ArrayList;
import java.util.Collection;

import org.projectmaxs.module.smssend.database.SmsTable;
import org.projectmaxs.shared.global.Message;
import org.projectmaxs.shared.global.util.Log;
import org.projectmaxs.shared.mainmodule.Command;
import org.projectmaxs.shared.global.messagecontent.Contact;
import org.projectmaxs.shared.global.messagecontent.ContactNumber;
import org.projectmaxs.shared.mainmodule.ModuleInformation;
import org.projectmaxs.shared.module.ContactUtil;
import org.projectmaxs.shared.module.MAXSModuleIntentService;
import org.projectmaxs.shared.module.RecentContactUtil;
import org.projectmaxs.shared.module.RecentContactUtil.RecentContact;
import org.projectmaxs.shared.module.SmsWriteUtil;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public class ModuleService extends MAXSModuleIntentService {

	private final static Log LOG = Log.getLog();

	public ModuleService() {
		super(LOG, "maxs-module-smssend");
	}

	// @formatter:off
	public static final ModuleInformation sMODULE_INFORMATION = new ModuleInformation(
			"org.projectmaxs.module.smssend",      // Package of the Module
			"smssend",                             // Name of the Module (if omitted, last substring after '.' is used)
			new ModuleInformation.Command[] {        // Array of commands provided by the module
					new ModuleInformation.Command(
							"sms",             // Command name
							"s",                    // Short command name
							null,                // Default subcommand without arguments
							"send",                    // Default subcommand with arguments
							new String[] { "send" }),  // Array of provided subcommands 
			});
	// @formatter:on

	public static final String PACKAGE = sMODULE_INFORMATION.getModulePackage();
	public static final String PART_NUM_EXTRA = "partNum";
	public static final String CMD_ID_EXTRA = "cmdId";

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public Message handleCommand(Command command) {
		String subCommand = command.getSubCommand();
		if (!"send".equals(subCommand)) throw new IllegalStateException("unkown command");

		String[] argsSplit = command.getArgs().split("  ", 2);
		Contact contact;
		String text;
		if (argsSplit.length == 1) {
			RecentContact recentContact = RecentContactUtil.getRecentContact(this);
			if (recentContact.mContactInfo == null) return new Message("No recent contact");
			if (recentContact.mContact != null) {
				contact = recentContact.mContact;
			}
			else {
				contact = new Contact();
			}
			if (ContactNumber.isNumber(recentContact.mContactInfo)) {
				contact.addNumber(recentContact.mContactInfo);
			}
			else {
				// If the contact info is not a number, e.g. because we received
				// an SMS with a company name as sender, then try to fill in the
				// missing information
				ContactUtil.getInstance(this).lookupContactNumbersFor(contact);
				if (contact.hasNumbers()) {
					return new Message("No number for contact");
				}
			}
			text = argsSplit[0];
		}
		else {
			Collection<Contact> contacts = ContactUtil.getInstance(this).lookupContacts(argsSplit[0]);
			if (contacts == null) {
				return new Message("Contacts module not installed?");
			}
			else if (contacts.size() > 1) {
				return new Message("Many matching contacts found");
			}
			else if (contacts.size() == 0) {
				return new Message("No matching contact found");
			}
			contact = contacts.iterator().next();
			text = argsSplit[1];
		}

		sendSMS(contact, text, command.getId());

		return new Message("Sending SMS to " + contact + ": " + text);
	}

	@Override
	public void initLog(Context context) {
		LOG.initialize(Settings.getInstance(context));
	}

	private void sendSMS(Contact contact, String text, int cmdId) {
		String receiver = contact.getBestNumber(ContactNumber.NumberType.MOBILE).getNumber();
		SmsManager smsManager = SmsManager.getDefault();
		Settings settings = Settings.getInstance(this);
		ArrayList<PendingIntent> sentIntents = null;
		ArrayList<PendingIntent> deliveryIntents = null;
		ArrayList<String> parts = smsManager.divideMessage(text);
		int partCount = parts.size();
		SmsTable smsTable = SmsTable.getInstance(this);
		boolean notifySentEnabled = settings.notifySentEnabled();
		boolean notifyDeliveredEnabled = settings.notifyDeliveredEnabled();

		if (notifySentEnabled || notifyDeliveredEnabled) {
			smsTable.addSms(cmdId, receiver, text.substring(0, 20), partCount, notifySentEnabled,
					notifyDeliveredEnabled);
			if (notifySentEnabled) {
				sentIntents = createPendingIntents(partCount, cmdId, SMSPendingIntentReceiver.SMS_SENT_ACTION,
						settings.getSentIntentRequestCode(partCount));
			}
			if (notifyDeliveredEnabled) {
				deliveryIntents = createPendingIntents(partCount, cmdId, SMSPendingIntentReceiver.SMS_DELIVERED_ACTION,
						settings.getDeliveredIntentRequestCode(partCount));
			}
		}

		smsManager.sendMultipartTextMessage(receiver, null, parts, sentIntents, deliveryIntents);
		RecentContactUtil.setRecentContact(receiver, contact, this);
		SmsWriteUtil.insertSmsInSystemDB(contact, text, this);
	}

	private ArrayList<PendingIntent> createPendingIntents(int size, int cmdId, String action, int requestCodeStart) {
		ArrayList<PendingIntent> intents = new ArrayList<PendingIntent>(size);
		for (int i = 0; i < size; i++) {
			Intent intent = new Intent(action);
			intent.putExtra(PART_NUM_EXTRA, i);
			intent.putExtra(CMD_ID_EXTRA, cmdId);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCodeStart, intent,
					PendingIntent.FLAG_ONE_SHOT);
			intents.add(pendingIntent);
		}
		return intents;
	}
}
