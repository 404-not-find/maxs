package org.projectmaxs.shared.aidl;

import org.projectmaxs.shared.Contact;
import org.projectmaxs.shared.ModuleInformation;
import org.projectmaxs.shared.xmpp.XMPPMessage;

interface IMAXSService {

    // recent contact
    Contact getRecentContact();

    // alias
    Contact getContactFromAlias(String alias);

}
