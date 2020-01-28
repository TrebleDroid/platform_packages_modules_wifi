/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi.hotspot2;

import android.text.TextUtils;

import com.android.server.wifi.IMSIParameter;
import com.android.server.wifi.hotspot2.anqp.CellularNetwork;
import com.android.server.wifi.hotspot2.anqp.DomainNameElement;
import com.android.server.wifi.hotspot2.anqp.NAIRealmData;
import com.android.server.wifi.hotspot2.anqp.NAIRealmElement;
import com.android.server.wifi.hotspot2.anqp.RoamingConsortiumElement;
import com.android.server.wifi.hotspot2.anqp.ThreeGPPNetworkElement;

import java.util.List;

/**
 * Utility class for providing matching functions against ANQP elements.
 */
public class ANQPMatcher {
    /**
     * Match the domain names in the ANQP element against the provider's FQDN and SIM credential.
     * The Domain Name ANQP element might contain domains for 3GPP network (e.g.
     * wlan.mnc*.mcc*.3gppnetwork.org), so we should match that against the provider's SIM
     * credential if one is provided.
     *
     * @param element The Domain Name ANQP element
     * @param fqdn The FQDN to compare against
     * @param imsiParam The IMSI parameter of the provider
     * @param simImsi The IMSI from the installed SIM cards that best matched provider's
     *                    IMSI parameter
     * @return true if a match is found
     */
    public static boolean matchDomainName(DomainNameElement element, String fqdn,
            IMSIParameter imsiParam, String simImsi) {
        if (element == null) {
            return false;
        }

        for (String domain : element.getDomains()) {
            if (DomainMatcher.arg2SubdomainOfArg1(fqdn, domain)) {
                return true;
            }

            // Try to retrieve the MCC-MNC string from the domain (for 3GPP network domain) and
            // match against the provider's SIM credential.
            if (matchMccMnc(Utils.getMccMnc(Utils.splitDomain(domain)), imsiParam, simImsi)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match the roaming consortium OIs in the ANQP element against the roaming consortium OIs
     * of a provider.
     *
     * @param element The Roaming Consortium ANQP element
     * @param providerOIs The roaming consortium OIs of the provider
     * @return true if a match is found
     */
    public static boolean matchRoamingConsortium(RoamingConsortiumElement element,
            long[] providerOIs) {
        if (element == null) {
            return false;
        }
        if (providerOIs == null) {
            return false;
        }
        List<Long> rcOIs = element.getOIs();
        for (long oi : providerOIs) {
            if (rcOIs.contains(oi)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match the NAI realm in the ANQP element against the realm and authentication method of
     * a provider.
     *
     * @param element The NAI Realm ANQP element
     * @param realm The realm of the provider's credential
     * @return an integer indicating the match status
     */
    public static int matchNAIRealm(NAIRealmElement element, String realm) {
        if (element == null || element.getRealmDataList().isEmpty()) {
            return AuthMatch.INDETERMINATE;
        }

        for (NAIRealmData realmData : element.getRealmDataList()) {
            if (matchNAIRealmData(realmData, realm) == AuthMatch.REALM) {
                return AuthMatch.REALM;
            }
        }
        return AuthMatch.NONE;
    }

    /**
     * Match the 3GPP Network in the ANQP element against the SIM credential of a provider.
     *
     * @param element 3GPP Network ANQP element
     * @param imsiParam The IMSI parameter of the provider's SIM credential
     * @param simImsi The IMSI from the installed SIM cards that best matched provider's
     *                    IMSI parameter
     * @return true if a matched is found
     */
    public static  boolean matchThreeGPPNetwork(ThreeGPPNetworkElement element,
            IMSIParameter imsiParam, String simImsi) {
        if (element == null) {
            return false;
        }
        for (CellularNetwork network : element.getNetworks()) {
            if (matchCellularNetwork(network, imsiParam, simImsi)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match the given NAI Realm data against the realm and authentication method of a provider.
     *
     * @param realmData The NAI Realm data
     * @param realm The realm of the provider's credential
     * @return an integer indicating the match status
     */
    private static int matchNAIRealmData(NAIRealmData realmData, String realm) {
        // Check for realm domain name match.
        for (String realmStr : realmData.getRealms()) {
            if (DomainMatcher.arg2SubdomainOfArg1(realm, realmStr)) {
                return AuthMatch.REALM;
            }
        }

        return AuthMatch.NONE;
    }

    /**
     * Match a cellular network information in the 3GPP Network ANQP element against the SIM
     * credential of a provider.
     *
     * @param network The cellular network that contained list of PLMNs
     * @param imsiParam IMSI parameter of the provider
     * @param simImsi The IMSI from the installed SIM cards that best matched provider's
     *                    IMSI parameter
     * @return true if a match is found
     */
    private static boolean matchCellularNetwork(CellularNetwork network, IMSIParameter imsiParam,
            String simImsi) {
        for (String plmn : network.getPlmns()) {
            if (matchMccMnc(plmn, imsiParam, simImsi)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Match a MCC-MNC against the SIM credential of a provider.
     *
     * @param mccMnc The string containing MCC-MNC
     * @param imsiParam The IMSI parameter of the provider
     * @param simImsi The IMSI from the installed SIM cards that best matched provider's
     *                    IMSI parameter
     * @return true if a match is found
     */
    private static boolean matchMccMnc(String mccMnc, IMSIParameter imsiParam,
            String simImsi) {
        if (imsiParam == null || TextUtils.isEmpty(simImsi) || mccMnc == null) {
            return false;
        }
        // Match against the IMSI parameter in the provider.
        if (!imsiParam.matchesMccMnc(mccMnc)) {
            return false;
        }
        // Additional check for verifying the match with IMSI from the SIM card, since the IMSI
        // parameter might not contain the full 6-digit MCC MNC (e.g. IMSI parameter is an IMSI
        // prefix that contained less than 6-digit of numbers "12345*").
        return simImsi.startsWith(mccMnc);
    }
}
