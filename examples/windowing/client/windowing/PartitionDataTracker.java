/* This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */


package windowing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.voltdb.VoltTable;
import org.voltdb.client.ProcCallException;

public class PartitionDataTracker implements Runnable {

    final GlobalState state;

    public PartitionDataTracker(GlobalState state) {
        this.state = state;
    }

    @Override
    public void run() {
        try {
            Map<Long, GlobalState.PartitionInfo> partitionData = new HashMap<Long, GlobalState.PartitionInfo>();

            VoltTable partitionKeys = null, tableStats = null;

            try {
                tableStats = state.client.callProcedure("@Statistics", "TABLE").getResults()[0];
                partitionKeys = state.client.callProcedure("@GetPartitionKeys", "STRING").getResults()[0];
            }
            catch (IOException | ProcCallException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return;
            }

            while (tableStats.advanceRow()) {
                if (!tableStats.getString("TABLE_NAME").equalsIgnoreCase("timedata")) {
                    continue;
                }

                GlobalState.PartitionInfo pinfo = new GlobalState.PartitionInfo();
                long partitionId = tableStats.getLong("PARTITION_ID");
                pinfo.tupleCount = tableStats.getLong("TUPLE_COUNT");
                pinfo.partitionKey = null;

                // If redundancy (k-safety) is enabled, this will put k+1 times per partition,
                // but the tuple count will be the same so it will be ok.
                partitionData.put(partitionId, pinfo);
            }

            while (partitionKeys.advanceRow()) {
                long partitionId = partitionKeys.getLong("PARTITION_ID");
                GlobalState.PartitionInfo pinfo = partitionData.get(partitionId);
                if (pinfo == null) {
                    // The set of partitions from the two calls don't match.
                    // Try again next time this is called... Maybe things
                    // will have settled down.
                    return;
                }

                pinfo.partitionKey = partitionKeys.getString("PARTITION_KEY");
            }

            // this is a sanity check to see that every partition has
            // a partition value
            boolean allMatched = true;
            for (GlobalState.PartitionInfo pinfo : partitionData.values()) {
                // a partition has a count, but no key
                if (pinfo.partitionKey == null) {
                    allMatched = false;
                }
            }
            if (!allMatched) {
                // The set of partitions from the two calls don't match.
                // Try again next time this is called... Maybe things
                // will have settled down.
                return;
            }

            // atomically update the new map for the old one
            state.updatePartitionInfoAndRedundancy(partitionData, tableStats.getRowCount() / partitionData.size());
        }
        catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
