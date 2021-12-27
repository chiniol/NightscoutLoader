package loader;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import control.MyLogger;
import entity.DBResult;
import mongo.NightscoutMongoDB;

// This class is responsible for keeping track of the uploads done on the Mongo DB
// We install a new document to hold this information.
//
// Eventually, the deletes will offer selection panel of previous loads for deletion
//
public class AuditHistory 
{
	private static final Logger m_Logger = Logger.getLogger(MyLogger.class.getName());

	private ArrayList<AuditLog> m_AuditHistoryList;
	
	private DataLoadNightScoutTreatments  m_NightScoutServerTest = new DataLoadNightScoutTreatments();

	private String              m_UploadIDPrefix  = "Nightscout Loader";
	private String              m_NextUploadID    = new String();

	// Implements the Singleton Design Pattern
	private static AuditHistory m_Instance=null;

	public static AuditHistory getInstance()
	{
		if (m_Instance == null)
		{
			m_Instance = new AuditHistory();
		}
		return m_Instance;
	}

	public void storeAuditHistory(String uploadStatus, Date uploadDate, 
			String uploadDevice, String fileName, String dateRange, int entriesAdded, 
			int treatmentsAtStart, int treatmentsByNSLAtStart, int proximityEntries,
			int proximityNSEntries, int cgmEntriesAdded) throws UnknownHostException
	{
		AuditLog newLog = new AuditLog("", getM_NextUploadID(), uploadStatus, uploadDate, uploadDevice, 
				fileName, dateRange, entriesAdded,	treatmentsAtStart, treatmentsByNSLAtStart, proximityEntries,
				proximityNSEntries, cgmEntriesAdded);

		storeAuditHistory(newLog);
	}


	public void storeAuditHistory(AuditLog entry) throws UnknownHostException
	{
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		// Performance improvement ...
		// http://stackoverflow.com/questions/18128490/how-to-insert-multiple-documents-at-once-in-mongodb-through-java
		List<DBObject> documents = new ArrayList<>();


		BasicDBObject doc = entry.createNightScoutObject();

		// coll.insert(doc);
		documents.add(doc);

		m_Logger.log(Level.FINEST, "Result added for Nightscout " + entry.toString());

		m_Logger.log(Level.FINE, "About to bulk insert to Nightscout");
		// Bulk insert instead
		
		// David 23 Sep 2021
		// This fails on Laptop with this stack trace
		
		/*
David determineCGMDifferences: Entries in m_NightScoutArrayListDBResultEntriesSet: 18855
Exception in thread "Thread-9" com.mongodb.WriteConcernException: Write failed with error code 79 and error message 'No write concern mode named 'majority ' found in replica set configuration'
	at com.mongodb.operation.BaseWriteOperation.convertBulkWriteException(BaseWriteOperation.java:198)
	at com.mongodb.operation.BaseWriteOperation.execute(BaseWriteOperation.java:155)
	at com.mongodb.operation.BaseWriteOperation.execute(BaseWriteOperation.java:52)
	at com.mongodb.client.internal.MongoClientDelegate$DelegateOperationExecutor.execute(MongoClientDelegate.java:206)
	at com.mongodb.client.internal.MongoClientDelegate$DelegateOperationExecutor.execute(MongoClientDelegate.java:175)
	at com.mongodb.DBCollection.executeWriteOperation(DBCollection.java:356)
	at com.mongodb.DBCollection.insert(DBCollection.java:351)
	at com.mongodb.DBCollection.insert(DBCollection.java:342)
	at com.mongodb.DBCollection.insert(DBCollection.java:311)
	at com.mongodb.DBCollection.insert(DBCollection.java:274)
	at com.mongodb.DBCollection.insert(DBCollection.java:257)
	at loader.AuditHistory.storeAuditHistory(AuditHistory.java:84)
	at loader.AuditHistory.storeAuditHistory(AuditHistory.java:61)
	at control.ThreadDetermineSaveDifferences.updateAuditHistory(ThreadDetermineSaveDifferences.java:709)
	at control.ThreadDetermineSaveDifferences.run(ThreadDetermineSaveDifferences.java:236)
	at java.base/java.lang.Thread.run(Thread.java:832)		
		*/
		
		/*
		WriteConcern wConcern = WriteConcern.valueOf("SAFE");
		coll.insert(documents, wConcern);
		*/
		
		
		coll.insert(documents);

		
		m_Logger.log(Level.FINE, "Bulk Insert completed.");

		nsMongoDB.close();
	}


	public void markAuditHistoryDeleted(AuditLog entry) throws UnknownHostException
	{
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		m_Logger.log(Level.FINEST, "Result added for Nightscout " + entry.toString());

		m_Logger.log(Level.FINE, "About to update Audit Log in Nightscout");

		BasicDBObject dbObject = new BasicDBObject();
		dbObject.put("_id", new ObjectId(entry.getM_ID()));

		BasicDBObject newObject = entry.createNightScoutObject();
		newObject.put("uploadStatus", AuditLog.m_DeletedBy + getM_NextUploadID());

		coll.findAndModify(dbObject, newObject);
		m_Logger.log(Level.FINE, "Audit Log update completed.");

		nsMongoDB.close();
	}

	public void markAuditHistoryDeleted(AuditLog entry, boolean proximityOnly) throws UnknownHostException
	{
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		m_Logger.log(Level.FINEST, "Result added for Nightscout " + entry.toString());

		m_Logger.log(Level.FINE, "About to update Audit Log in Nightscout");
		
		BasicDBObject dbObject = new BasicDBObject();
		dbObject.put("_id", new ObjectId(entry.getM_ID()));

		BasicDBObject newObject = entry.createNightScoutObject();
		newObject.put("uploadStatus", (proximityOnly ? AuditLog.m_ProximityDeletedBy : AuditLog.m_DeletedBy)
				+ getM_NextUploadID());

		coll.findAndModify(dbObject, newObject);
		m_Logger.log(Level.FINE, "Audit Log update completed.");

		nsMongoDB.close();
	}

	public void markAuditHistoryProximityReduced(AuditLog entry) throws UnknownHostException
	{
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		m_Logger.log(Level.FINEST, "Result added for Nightscout " + entry.toString());

		m_Logger.log(Level.FINE, "About to update Audit Log in Nightscout");
		
		BasicDBObject dbObject = new BasicDBObject();
		dbObject.put("_id", new ObjectId(entry.getM_ID()));

		BasicDBObject newObject = entry.createNightScoutObject();
		newObject.put("meterProximityEntries", entry.getM_ProximityMeterEntries() - 1);
		
		coll.findAndModify(dbObject, newObject);
		m_Logger.log(Level.FINE, "Audit Log update completed.");

		nsMongoDB.close();
	}
	
	public void markAllAuditHistoryDeleted() throws UnknownHostException
	{
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		for (AuditLog entry: this.m_AuditHistoryList)
		{			
			AuditLog.Status status = entry.getStatus();
			boolean activeRecord = (status == AuditLog.Status.Success || status == AuditLog.Status.Not_Saved) ? true : false;

			if (activeRecord)
			{
				m_Logger.log(Level.FINEST, "AuditLog Result being updated for Nightscout " + entry.toString());

				m_Logger.log(Level.FINE, "About to update AuditLog in Nightscout");

				BasicDBObject dbObject = new BasicDBObject();
				dbObject.put("_id", new ObjectId(entry.getM_ID()));

				BasicDBObject newObject = entry.createNightScoutObject();
				newObject.put("uploadStatus", AuditLog.m_DeletedBy + getM_NextUploadID());

				coll.findAndModify(dbObject, newObject);
				m_Logger.log(Level.FINE, "Audit Log update completed.");
			}
		}

		nsMongoDB.close();
	}

	public void loadAuditHistory() throws UnknownHostException
	{
		m_AuditHistoryList = new ArrayList<AuditLog>();
		
		NightscoutMongoDB nsMongoDB = new NightscoutMongoDB();
		DBCollection coll = nsMongoDB.getAuditV2xCollection();

		String timeFld = new String();
		timeFld = "created_at";

		BasicDBObject query = new BasicDBObject();

		String qry = query.toString();

		m_Logger.log(Level.FINE, "loadAuditHistory Mongo Query is now " + qry );

		DBCursor cursor = coll.find(query);
		// Sort by time
		cursor.sort(new BasicDBObject(timeFld, 1));

		for (DBObject rs: cursor)
		{			
			// Now create Result objects for each document and store into array
			//ResultFromDB res = new ResultFromDB(rs);

			// Switch to new object type
			AuditLog res = new AuditLog(rs, false);

			m_AuditHistoryList.add(res);

			m_Logger.log(Level.FINEST, "Result added for Nightscout " + rs.toString());
		}

		// Then sort the results, just to be sure they're sorted
		sortAuditHistory();

		// Having loaded history & sorted results, now determine the next upload id		
		m_NextUploadID = allocateNextUploadID();

		nsMongoDB.close();
	}
	
	public void clearProximity(DBResult res) throws UnknownHostException
	{
		// Iterate over the audit log and find the matching entry
		String uploadID = new String(res.getM_CP_EnteredBy());
		AuditLog auditLog = null;
		
		for (AuditLog c : m_AuditHistoryList)
		{
			if (c.getM_UploadID().equals(uploadID))
			{
				auditLog = c;				
				break;
			}
		}
		
		if (auditLog != null)
		{
			markAuditHistoryProximityReduced(auditLog);
		}
	}

	/**
	 * @return the m_NextUploadID
	 */
	public synchronized String getM_NextUploadID() {
		return m_NextUploadID;
	}

	/**
	 * @return the m_UploadIDPrefix
	 */
	public synchronized String getM_UploadIDPrefix() {
		return m_UploadIDPrefix;
	}

	/**
	 * @param m_UploadIDPrefix the m_UploadIDPrefix to set
	 */
	public synchronized void setM_UploadIDPrefix(String m_UploadIDPrefix) {
		this.m_UploadIDPrefix = m_UploadIDPrefix;
	}

	/**
	 * @return the m_AuditHistoryList
	 */
	public synchronized ArrayList<AuditLog> getM_AuditHistoryList() {
		return m_AuditHistoryList;
	}

	/**
	 * @param m_AuditHistoryList the m_AuditHistoryList to set
	 */
	public synchronized void setM_AuditHistoryList(ArrayList<AuditLog> m_AuditHistoryList) {
		this.m_AuditHistoryList = m_AuditHistoryList;
	}

	private AuditHistory()
	{
		// Load results from Mongo & refresh the next upload ID
		try
		{
			// Use the DataNightScout load to check server state
			m_NightScoutServerTest.testMongo();
			if (m_NightScoutServerTest.getM_ServerState() == DataLoadNightScoutTreatments.MongoDBServerStateEnum.accessible)
			{
				loadAuditHistory();
			}
		} 
		catch (UnknownHostException e) 
		{
			m_Logger.log(Level.SEVERE, "<"+this.getClass().getName()+">" + ". just caught an error downloading treatments: " + e.getMessage() + " - " + e.getLocalizedMessage());
		}
	}



	private void sortAuditHistory()
	{
		// Sort the Mongo Results in Ascending order
		Collections.sort(m_AuditHistoryList, new AuditLogComparator(false));
	}

	// Have audithistorylist sorted by date and then retrieve the last upload ID,
	// Increment, store back to Mongo to ensure it's reserved and then return back
	private String allocateNextUploadID()
	{
		String result  = new String();
		int    counter = 0;  // Initialize counter for upload ids

		if (m_AuditHistoryList.size() > 0)
		{
			AuditLog lastEntry = m_AuditHistoryList.get(m_AuditHistoryList.size() - 1);

			String uploadId = lastEntry.getM_UploadID();

			// Upload IDS are in the form:
			//
			// NightScoutLoader:<nn>:<mmmmmm>
			//
			// Where nn is an incremental number
			//   and mm is the number of milliseconds since 1 Jan 2016 - NightScoutLoader birthday!

			// field is in the form 2dd/mm/yyyy to dd/mm/yyyy"
			Pattern uploadIDPattern = Pattern.compile(m_UploadIDPrefix + ":([0-9]*):([0-9]*)");
			Matcher uploadIDMatcher = uploadIDPattern.matcher(uploadId);

			if (uploadIDMatcher.find())
			{
				String counterString = uploadIDMatcher.group(1);
				//				String milliString   = uploadIDMatcher.group(2);
				// Convert to int
				counter = Integer.parseInt(counterString);
			}
		}

		// Increment the counter
		counter++;

		// Now generate next ID
		// Note that it's possible someone else is loading at same time, so add current seconds
		// of current time which *should* be unique.
		Date now = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);  
		int seconds = cal.get(Calendar.SECOND);
		result = m_UploadIDPrefix + ":" + counter + ":" + seconds;

		return result;
	}
}
