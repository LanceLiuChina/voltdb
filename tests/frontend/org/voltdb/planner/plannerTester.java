package org.voltdb.planner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jetty.util.ajax.JSON;
import org.json_voltpatches.JSONArray;
import org.json_voltpatches.JSONException;
import org.json_voltpatches.JSONObject;
import org.voltdb.catalog.CatalogMap;
import org.voltdb.catalog.Cluster;
import org.voltdb.catalog.Table;
import org.voltdb.plannodes.AbstractPlanNode;
import org.voltdb.plannodes.PlanNodeTree;
import org.voltdb.types.PlanNodeType;

public class plannerTester {
	private static PlannerTestAideDeCamp aide;
	private static String m_ddlFile = "testplans-plannerTester-ddl.sql";
	private static String m_stmtFile = "testplans-plannerTester-ddl.stmt";
	private static ArrayList<String> m_stmts = new ArrayList<String>();

    public static void setUpSchema( String ddl, String basename ) throws Exception {
        aide = new PlannerTestAideDeCamp(TestIndexSelection.class.getResource(ddl),
        		basename);

        // Set all tables to non-replicated.
        Cluster cluster = aide.getCatalog().getClusters().get("cluster");
        CatalogMap<Table> tmap = cluster.getDatabases().get("database").getTables();
        for (Table t : tmap) {
            t.setIsreplicated(false);
        }
    }
    
    public static void loadStmts ( String stmtFile ) throws IOException {
    	m_stmts.clear();
    	String line = null;
    	BufferedReader reader = new BufferedReader( new FileReader( TestIndexSelection.class.getResource(stmtFile).getPath() ) );
    	while( ( line = reader.readLine() ) != null ) {
    		m_stmts.add( line );
    	}
    }
    
    //assumes single partition
    public static void batchCompileSave( String ddl, String basename, String stmtFilePath, String savePath ) throws Exception {
    	setUpSchema( ddl, basename );
    	loadStmts( stmtFilePath );
    	int size = m_stmts.size();
    	for( int i = 0; i < size; i++ ) {
    		AbstractPlanNode pn = compile( m_stmts.get(i), 0, true);
    		writePlanToFile(pn, savePath+i );
    	}
    }

    protected void tearDown() throws Exception {
        aide.tearDown();
    }
    
	public static AbstractPlanNode compile( String sql, int paramCount,
            boolean singlePartition ) throws Exception {
		List<AbstractPlanNode> pn = null;
        pn =  aide.compile(sql, paramCount, singlePartition);
        return pn.get(0);
	}
	
	public static void writePlanToFile( AbstractPlanNode pn, String path ) {
		if( pn == null ) {
			System.err.println("the plan node is null, nothing to write");
			System.exit(-1);
		}
		PlanNodeTree pnt = new PlanNodeTree( pn );
		String prettyJson = pnt.toJSONString();
        try {
		    	BufferedWriter writer = new BufferedWriter( new FileWriter( path ) );
		    	writer.write( prettyJson );
		    	writer.flush();
		    	writer.close();
	   	} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static PlanNodeTree loadPlanFromFile( String path ) {
		PlanNodeTree pnt = new PlanNodeTree();
		String prettyJson = "";
		String line = null;
		 try {
				BufferedReader reader = new BufferedReader( new FileReader( path ));
				while( (line = reader.readLine() ) != null ){
					line = line.trim();
					prettyJson += line;
				}
			}
	        catch (IOException e) {
	    		e.printStackTrace();
	    	}
			JSONObject jobj;
			try {
				jobj = new JSONObject( prettyJson );
				JSONArray jarray = 	jobj.getJSONArray("PLAN_NODES");
				pnt.loadFromJSONArray(jarray);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		return pnt;
	}
	
	//parameters : path to baseline and the new plans
	public static void batchDiffLeaves( String pathBaseline, String pathNew, int size ) {
		PlanNodeTree pnt1 = null;
		PlanNodeTree pnt2 = null;
		for( int i = 0; i < size; i++ ){
			pnt1 = loadPlanFromFile( pathBaseline );
			pnt2  = loadPlanFromFile( pathNew );
			diffLeaves(pnt1.getRootPlanNode(), pnt2.getRootPlanNode());
		}
	}
	
	public static void diffLeaves( AbstractPlanNode oldpn1, AbstractPlanNode newpn2 ){
		ArrayList<AbstractPlanNode> list1 = oldpn1.getLeafLists();
		ArrayList<AbstractPlanNode> list2 = newpn2.getLeafLists();
		int size1 = list1.size();
		int size2 = list2.size();
		int max = Math.max(size1, size2);
		int min = Math.min(size1, size2);
		if( size1 != size2 ){
			System.out.println( "Different size, used to be: "+size1+", now is: "+size2 );
			try {
				for( int i = 0; i < min; i++ ) {
					JSONObject j1 = new JSONObject( list1.get(i).toJSONString() );
					JSONObject j2 = new JSONObject( list2.get(i).toJSONString() );
					String table1 = j1.getString("TARGET_TABLE_NAME");
					String table2 = j2.getString("TARGET_TABLE_NAME");
					String nodeType1 = j1.getString("PLAN_NODE_TYPE");
					String nodeType2 = j2.getString("PLAN_NODE_TYPE");
					if( !table1.equalsIgnoreCase(table2) )
						System.out.println("Different table at "+i+" used to be: "+nodeType1+" at "+table1+", now is: "+nodeType2+" at "+table2);
					else if( !nodeType1.equalsIgnoreCase(nodeType2) ) {
						System.out.println("Different scan at "+i+" used to be: "+nodeType1+" at "+table1+", now is: "+nodeType2+" at "+table2);
					}
					else if ( nodeType1.equalsIgnoreCase("INDEXSCAN") ) {
						String index1 = j1.getString("TARGET_INDEX_NAME");
						String index2 = j2.getString("TARGET_INDEX_NAME");
						if( !index1.equalsIgnoreCase(index2) )
							System.out.println("Different index at "+i+" used to be: "+index1+", now is: "+index2);
					}
					else
						System.out.println("Same at "+i);
				}
				//lists size are different
				if( size2 < max ) {
					for( int i = min; i < max; i++ ) {
						JSONObject j = new JSONObject( list1.get(i).toJSONString() );
						String table = j.getString("TARGET_TABLE_NAME");
						String nodeType = j.getString("PLAN_NODE_TYPE");
						String index = null;
						if( nodeType.equalsIgnoreCase("INDEXSCAN") )
						  index = j.getString("TARGET_INDEX_NAME");
						System.out.println("Different at "+i+" used to be table: "+table+" type :"+nodeType+" " +
								" index: "+index+" now is empty");
		 			}
				}
				else if( size1 < max ) {
					for( int i = min; i < max; i++ ) {
						JSONObject j = new JSONObject( list2.get(i).toJSONString() );
						String table = j.getString("TARGET_TABLE_NAME");
						String nodeType = j.getString("PLAN_NODE_TYPE");
						String index = null;
						if( nodeType.equalsIgnoreCase("INDEXSCAN") ) {
						  index = j.getString("TARGET_INDEX_NAME");
						  System.out.println("Different at "+i+" used to be empty, now is :"+nodeType+" " + " at "+table+" on index "+index);
						}
						else
							System.out.println("Different at "+i+" used to be empty, now is :"+nodeType+" at "+table);
		 			}
				}
				}
				catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		else {
			System.out.println("same size!");
			try{
				if( max == 1 ) {
					System.out.println("single table query~");
					JSONObject j1 = new JSONObject( list1.get(0).toJSONString() );
					JSONObject j2 = new JSONObject( list2.get(0).toJSONString() );
					String table1 = j1.getString("TARGET_TABLE_NAME");
					String table2 = j2.getString("TARGET_TABLE_NAME");
					String nodeType1 = j1.getString("PLAN_NODE_TYPE");
					String nodeType2 = j2.getString("PLAN_NODE_TYPE");
					if( !table1.equalsIgnoreCase(table2) )
						System.out.println("Different table at "+0+" used to be: "+nodeType1+" at "+table1+", now is: "+nodeType2+" at "+table2);
					else if( !nodeType1.equalsIgnoreCase(nodeType2) ) {
						System.out.println("Different scan at "+0+" used to be: "+nodeType1+", now is: "+nodeType2);
					}
					else if ( nodeType1.equalsIgnoreCase("INDEXSCAN") ) {
						String index1 = j1.getString("TARGET_INDEX_NAME");
						String index2 = j2.getString("TARGET_INDEX_NAME");
						if( !index1.equalsIgnoreCase(index2) )
							System.out.println("Different index at "+0+" used to be: "+index1+", now is: "+index2);
					}
					else
						System.out.println("Same at "+0);
				}
				else {
					System.out.println("Join Query:");
					for( int i = 0; i < max; i++ ) {
						JSONObject j1 = new JSONObject( list1.get(i).toJSONString() );
						JSONObject j2 = new JSONObject( list2.get(i).toJSONString() );
						String table1 = j1.getString("TARGET_TABLE_NAME");
						String table2 = j2.getString("TARGET_TABLE_NAME");
						String nodeType1 = j1.getString("PLAN_NODE_TYPE");
						String nodeType2 = j2.getString("PLAN_NODE_TYPE");
						if( !table1.equalsIgnoreCase(table2) )
							System.out.println("Different table at "+i+" used to be: "+nodeType1+" at "+table1+", now is: "+nodeType2+" at "+table2);
						else if( !nodeType1.equalsIgnoreCase(nodeType2) ) {
							System.out.println("Different scan at "+i+" used to be: "+nodeType1+", now is: "+nodeType2);
						}
						else if ( nodeType1.equalsIgnoreCase("INDEXSCAN") ) {
							String index1 = j1.getString("TARGET_INDEX_NAME");
							String index2 = j2.getString("TARGET_INDEX_NAME");
						if( !index1.equalsIgnoreCase(index2) )
							System.out.println("Different index at "+i+" used to be: "+index1+", now is: "+index2);
					}
					else
						System.out.println("Same at "+i);
				}
			}
			}
			catch ( JSONException e ) {
				e.printStackTrace();
			}
		}	
	}
}
