/* This file is part of VoltDB.
 * Copyright (C) 2008-2013 VoltDB Inc.
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

package org.voltdb.planner;

import org.voltdb.expressions.ExpressionUtil;
import org.voltdb.plannodes.AbstractPlanNode;
import org.voltdb.plannodes.IndexScanPlanNode;
import org.voltdb.plannodes.OrderByPlanNode;
import org.voltdb.plannodes.ProjectionPlanNode;
import org.voltdb.plannodes.SendPlanNode;
import org.voltdb.types.ExpressionType;
import org.voltdb.types.IndexLookupType;
import org.voltdb.types.SortDirectionType;

public class TestReverseIndexScan extends PlannerTestCase {
    @Override
    protected void setUp() throws Exception {
        final boolean planForSinglePartition = true;
        setupSchema(TestReverseIndexScan.class.getResource("testplans-indexvshash-ddl.sql"),
                    "testindexvshashplans", planForSinglePartition);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    String sql;


    public void testLegancyTests()
    {
        sql = "select a from t where a = ? and b < ?";
        checkReverseScan("COVER2_TREE", IndexLookupType.LT, 2, 1, 1, SortDirectionType.INVALID);

        sql = "select a from t where a = ? and b <= ?";
        checkReverseScan("COVER2_TREE", IndexLookupType.LTE, 2, 1, 1, SortDirectionType.INVALID);

        sql = "select a from t where a = ? and c = ? and b < ?;";
        checkReverseScan("COVER3_TREE", IndexLookupType.LT, 3, 2, 1, SortDirectionType.INVALID);

        sql = "select a from t where a = ? and c = ? and b <= ?";
        checkReverseScan("COVER3_TREE", IndexLookupType.LTE, 3, 2, 1, SortDirectionType.INVALID);

        // no ORDER BY node because order-by is handled by index scan inherent ordering
        sql = "select a, b from t where a = ? and c = ? and b < ? order by b desc;";
        checkReverseScan("COVER3_TREE", IndexLookupType.LT, 3, 2, 1, SortDirectionType.DESC);

        sql = "select a, b from t where a = ? and b = ? and c < ? order by c desc;";
        checkReverseScan("IDX_1_TREE", IndexLookupType.LT, 3, 2, 1, SortDirectionType.DESC);

        sql = "select a, b from t where a = ? and c = ? and b <= ? order by b desc;";
        checkReverseScan("COVER3_TREE", IndexLookupType.LTE, 3, 2, 1, SortDirectionType.DESC);

    }

    public void testLegancyTests_NonOptimizable()
    {
        // ORDER BY ASC: do not do reverse scan optimization
        sql = "select a, b from t where a = ? and c = ? and b < ? order by b;";
        checkForwardScan("COVER3_TREE", IndexLookupType.GTE, 2, 3, 0, SortDirectionType.ASC);

        // ORDER BY ASC: do not do reverse scan optimization
        sql = "select a, b from t where a = ? and c = ? and b <= ? order by b;";
        checkForwardScan("COVER3_TREE", IndexLookupType.GTE, 2, 3, 0, SortDirectionType.ASC);
    }

    public void testENG5297()
    {
        sql = "SELECT * FROM data_reports " +
                "WHERE appID = 1486287933647372287 AND reportID = 1526804868369481731 " +
                "AND metricID = 1486287935375409155 AND field = 'accountID' " +
                "AND time >= '2013-09-29 00:00:00' AND time <= '2013-10-07 00:00:00' " +
                "ORDER BY time DESC LIMIT 150";

        checkReverseScan("SYS_IDX_IDX_REPORTDATA_PK", IndexLookupType.LTE,
                3, 3, 2, SortDirectionType.DESC);
    }

    public void testNonOptimizable()
    {
        // One column index case.
        sql = "select * from R where a >= 1 AND a <= 9 ORDER BY b DESC";
        // Current code logic: if still needs order by node, the sort direction is Invalid.
        checkForwardScan("R1_TREE", IndexLookupType.GTE, 1, 1, 0, SortDirectionType.INVALID, true);

        // Have one extra post predicate because of ENG-3913. ???
        sql = "select * from R where a > 1 ORDER BY b desc";
        checkForwardScan("R1_TREE", IndexLookupType.GT, 1, 0, 0, SortDirectionType.INVALID, true);

        sql = "select * from R where a >= 1 ORDER BY b desc";
        checkForwardScan("R1_TREE", IndexLookupType.GTE, 1, 0, 0, SortDirectionType.INVALID, true);

        // Two columns index case.
        sql = "select * from R where b = 1 ORDER BY c";
        checkForwardScan("R2_TREE", IndexLookupType.GTE, 1, 1, 0, SortDirectionType.ASC);

        sql = "select * from R where b > 1 ORDER BY c desc";
        checkForwardScan("R2_TREE", IndexLookupType.GT, 1, 0, 1, SortDirectionType.INVALID, true);

        sql = "select * from R where b >= 1 ORDER BY c desc";
        checkForwardScan("R2_TREE", IndexLookupType.GTE, 1, 0, 0, SortDirectionType.INVALID, true);
    }

    public void testOneColumnIndex()
    {
        sql = "select * from R where a < 9";
        checkReverseScan("R1_TREE", IndexLookupType.LT, 1, 0, 1, SortDirectionType.INVALID);

        sql = "select * from R where a <= 9";
        checkReverseScan("R1_TREE", IndexLookupType.LTE, 1, 0, 1, SortDirectionType.INVALID);

        // Have one extra post predicate because of ENG-3913.
        sql = "select * from R where a > 1 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.GT, 0, 1, 1, SortDirectionType.DESC);

        sql = "select * from R where a >= 1 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.GTE, 0, 1, 0, SortDirectionType.DESC);

        sql = "select * from R where a > 1 AND a < 9 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.LT, 1, 1, 0, SortDirectionType.DESC);

        sql = "select * from R where a > 1 AND a <= 9 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.LTE, 1, 1, 0, SortDirectionType.DESC);

        sql = "select * from R where a >= 1 AND a < 9 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.LT, 1, 1, 0, SortDirectionType.DESC);

        sql = "select * from R where a >= 1 AND a <= 9 ORDER BY a desc";
        checkReverseScan("R1_TREE", IndexLookupType.LTE, 1, 1, 0, SortDirectionType.DESC);
    }

    public void testTwoColumnsIndex()
    {
        // Case 1: first column equal
        sql = "select * from R where b = 1 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 1, 0, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND c < 2 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 2, 1, 1, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND c <= 2 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 2, 1, 1, SortDirectionType.DESC);

        // Have one extra post predicate because of ENG-3913.
        sql = "select * from R where b = 1 AND C > 3 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.GT, 1, 2, 1, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND C >= 3 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.GTE, 1, 2, 0, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND C > 3 AND C < 6 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 2, 2, 0, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND C > 3 AND C <= 6 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 2, 2, 0, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND C >= 3 AND C < 6 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 2, 2, 0, SortDirectionType.DESC);

        sql = "select * from R where b = 1 AND C >= 3 AND C <= 6 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 2, 2, 0, SortDirectionType.DESC);


        // Case 2: first column less than
        sql = "select * from R where b < 1 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 1, SortDirectionType.DESC);
        sql = "select * from R where b <= 1 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 1, SortDirectionType.DESC);

       // The second column filter will be a post predicate, does not need to test all cases for the second column.
        sql = "select * from R where b <= 1 AND c < 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 2, SortDirectionType.DESC);
        sql = "select * from R where b < 1 AND c < 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 2, SortDirectionType.DESC);
        sql = "select * from R where b <= 1 AND c <= 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 2, SortDirectionType.DESC);
        sql = "select * from R where b < 1 AND c <= 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 2, SortDirectionType.DESC);
        sql = "select * from R where b < 1 AND c = 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 2, SortDirectionType.DESC);
        sql = "select * from R where b <= 1 AND c = 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 2, SortDirectionType.DESC);

        // Order by the second column will not remove the order by node.
        sql = "select * from R where b < 1 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 1, SortDirectionType.INVALID, true);
        sql = "select * from R where b <= 1 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 1, SortDirectionType.INVALID, true);
        sql = "select * from R where b <= 1 AND c < 2 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 2, SortDirectionType.INVALID, true);

        // The second column filter will be a post predicate, does not need to test all cases for the second column.
        sql = "select * from R where b < 1 AND c < 2 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LT, 1, 0, 2, SortDirectionType.INVALID, true);
        sql = "select * from R where b <= 1 AND c < 2 ORDER BY c desc";
        checkReverseScan("R2_TREE", IndexLookupType.LTE, 1, 0, 2, SortDirectionType.INVALID, true);
        // ...


        // Case 3: first column greater than
        // Have one extra post predicate because of ENG-3913.
        sql = "select * from R where b > 1 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GT, 0, 1, 1, SortDirectionType.DESC);

        sql = "select * from R where b >= 1 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GTE, 0, 1, 0, SortDirectionType.DESC);

        // The second column filter will be a post predicate, does not need to test all cases for the second column.
        sql = "select * from R where b > 1 AND c > 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GT, 0, 1, 2, SortDirectionType.DESC);
        sql = "select * from R where b >= 1 AND c > 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GTE, 0, 1, 1, SortDirectionType.DESC);
        sql = "select * from R where b > 1 AND c = 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GT, 0, 1, 2, SortDirectionType.DESC);
        sql = "select * from R where b >= 1 AND c = 2 ORDER BY b desc";
        checkReverseScan("R2_TREE", IndexLookupType.GTE, 0, 1, 1, SortDirectionType.DESC);
        // ...
    }

    private void checkReverseScan(String indexName, IndexLookupType lookupType,
            int searchKeys, int endKeys, int predicates, SortDirectionType sortType) {

        checkReverseScan(indexName, lookupType, searchKeys, endKeys, predicates, sortType, false);
    }


    private void checkReverseScan(String indexName, IndexLookupType lookupType,
            int searchKeys, int endKeys, int predicates, SortDirectionType sortType, boolean needOrderby) {
        AbstractPlanNode pn = compile(sql);
        System.out.println(pn.toExplainPlanString());

        assertTrue(pn instanceof SendPlanNode);
        pn = pn.getChild(0);
        if (needOrderby) {
            assertTrue(pn instanceof ProjectionPlanNode);
            pn = pn.getChild(0);
            assertTrue(pn instanceof OrderByPlanNode);
            pn = pn.getChild(0);
        }
        assertTrue(pn instanceof IndexScanPlanNode);

        IndexScanPlanNode ispn = (IndexScanPlanNode)pn;
        assertTrue(ispn.getTargetIndexName().contains(indexName));
        assertEquals(lookupType, ispn.getLookupType());
        assertEquals(searchKeys, ispn.getSearchKeyExpressions().size());
        assertEquals(endKeys, ExpressionUtil.uncombine(ispn.getEndExpression()).size());
        assertEquals(predicates, ExpressionUtil.uncombine(ispn.getPredicate()).size());

        // Test artificial post predicate
        if (predicates == 1) {
            // Rule out of case: (1) ENG-3913. (2) b >= 1 AND c > 2 ORDER BY b desc.
            if (lookupType != IndexLookupType.GT && lookupType != IndexLookupType.GTE) {
                assertTrue(ispn.getPredicate().getExpressionType() == ExpressionType.OPERATOR_NOT);
                assertTrue(ispn.getPredicate().getLeft().getExpressionType() == ExpressionType.OPERATOR_IS_NULL);
            }
        } else if (predicates > 1) {
            assertTrue(ispn.getPredicate().getExpressionType() == ExpressionType.CONJUNCTION_AND);
        }
        assertEquals(searchKeys, ExpressionUtil.uncombine(ispn.getInitialExpression()).size());

        // SortDirection can be INVALID because we use LookupType to determine
        // index scan direction instead in EE.
        assertEquals(sortType, ispn.getSortDirection());
    }



    private void checkForwardScan(String indexName, IndexLookupType lookupType,
            int searchKeys, int endKeys, int predicates, SortDirectionType sortType) {
        checkForwardScan(indexName, lookupType, searchKeys, endKeys, predicates, sortType, false);
    }

    private void checkForwardScan(String indexName, IndexLookupType lookupType,
            int searchKeys, int endKeys, int predicates, SortDirectionType sortType, boolean needOrderby) {
        AbstractPlanNode pn = compile(sql);
        System.out.println(pn.toExplainPlanString());

        assertTrue(pn instanceof SendPlanNode);
        pn = pn.getChild(0);

        if (needOrderby) {
            assertTrue(pn instanceof ProjectionPlanNode);
            pn = pn.getChild(0);
            assertTrue(pn instanceof OrderByPlanNode);
            pn = pn.getChild(0);
        }
        assertTrue(pn instanceof IndexScanPlanNode);

        IndexScanPlanNode ispn = (IndexScanPlanNode)pn;
        assertTrue(ispn.getTargetIndexName().contains(indexName));
        assertEquals(lookupType, ispn.getLookupType());
        assertEquals(searchKeys, ispn.getSearchKeyExpressions().size());
        assertEquals(endKeys, ExpressionUtil.uncombine(ispn.getEndExpression()).size());
        assertEquals(predicates, ExpressionUtil.uncombine(ispn.getPredicate()).size());

        assertEquals(0, ExpressionUtil.uncombine(ispn.getInitialExpression()).size());

        assertEquals(sortType, ispn.getSortDirection());
    }

}
