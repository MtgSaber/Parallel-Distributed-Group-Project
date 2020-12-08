package net.mtgsaber.uni_projects.cs4504groupproject.util;

import java.util.HashMap;
import java.util.Map;

public class Stats {
    // average message size inputs
    private static volatile long messageSizeCnt = 0;
    private static volatile int messageCnt = 0;

    // average transmission time inputs
    private static volatile long transmissionTimeCnt = 0;
    private static volatile int transmissionCnt = 0;

    // average routing table time inputs
    private static volatile long routingTableLookupTimeCnt = 0;
    private static volatile int routingTableLookupCnt = 0;

    // bytes per unit time inputs
    private static volatile long bytesTransferredCnt = 0;

    public static void incrementMessageSizeCnt(long messageSizeCnt) {
        Stats.messageSizeCnt += messageSizeCnt;
    }

    public static void incrementMessageCnt(int messageCnt) {
        Stats.messageCnt += messageCnt;
    }

    public static void incrementTransmissionTimeCnt(long transmissionTimeCnt) {
        Stats.transmissionTimeCnt += transmissionTimeCnt;
    }

    public static void incrementTransmissionCnt(int transmissionCnt) {
        Stats.transmissionCnt += transmissionCnt;
    }

    public static void incrementRoutingTableLookupTimeCnt(long routingTableLookupTimeCnt) {
        Stats.routingTableLookupTimeCnt += routingTableLookupTimeCnt;
    }

    public static void incrementRoutingTableLookupCnt(int routingTableLookupCnt) {
        Stats.routingTableLookupCnt += routingTableLookupCnt;
    }

    public static void incrementBytesTransferredCnt(long bytesTransferredCnt) {
        Stats.bytesTransferredCnt += bytesTransferredCnt;
    }

    public static Map<String, Double> evaluateStatistics() {

        Map<String, Double> results = new HashMap<>();
        results.put("avgMessageSize", (double)messageSizeCnt/(double)messageCnt);
        results.put("avgTransmissionTime", (double)transmissionTimeCnt/(double)messageCnt);
        results.put("avgRoutingTableLookupTime", (double)routingTableLookupTimeCnt/(double)routingTableLookupCnt);
        results.put("avgBytesTransferred",(double)messageSizeCnt/(double)transmissionTimeCnt);

        return results;
    }
}
