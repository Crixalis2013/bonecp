/**
 *  Copyright 2010 Wallace Wadge
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.jolbox.bonecp;


import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import com.jolbox.bonecp.hooks.CoverageHook;
import com.jolbox.bonecp.hooks.CustomHook;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
/**
 * @author wwadge
 *
 */
public class TestStatementHandle {
	/** Class under test. */
	private StatementHandle testClass;
	/** Mock class under test. */
	private PreparedStatementHandle mockClass;
	/** Mock handles. */
	private IStatementCache mockCallableStatementCache;
	/** Mock handles. */
	private ConnectionHandle mockConnection;
	/** Mock handle. */
	private BoneCPConfig mockConfig;
	/** Mock handle. */
	private BoneCP mockPool;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		mockClass = createNiceMock(PreparedStatementHandle.class);
		mockCallableStatementCache = createNiceMock(IStatementCache.class);
		mockConnection = createNiceMock(ConnectionHandle.class);
		mockConfig = createNiceMock(BoneCPConfig.class);
		
		mockPool = createNiceMock(BoneCP.class);
		expect(mockConnection.getPool()).andReturn(mockPool).anyTimes();
		expect(mockPool.getConfig()).andReturn(mockConfig).anyTimes();
		expect(mockPool.getStatistics()).andReturn(new Statistics(mockPool)).anyTimes();
		expect(mockConfig.isStatisticsEnabled()).andReturn(true).anyTimes();
		expect(mockConfig.isCloseOpenStatements()).andReturn(true).anyTimes();
		expect(mockConfig.getQueryExecuteTimeLimitInMs()).andReturn(1L).anyTimes();
		expect(mockConfig.getConnectionHook()).andReturn(new CoverageHook()).anyTimes();
		replay(mockConnection, mockPool, mockConfig);
		testClass = new StatementHandle(mockClass, "", mockCallableStatementCache, mockConnection, "testSQL", true);
		reset(mockClass, mockCallableStatementCache, mockConnection, mockPool);
		
	    Logger shMockLogger = TestUtils.mockLogger(testClass.getClass());
	    expect(shMockLogger.isDebugEnabled()).andReturn(true).anyTimes();
	    Logger pshMockLogger = TestUtils.mockLogger(PreparedStatementHandle.class);
		expect(pshMockLogger.isDebugEnabled()).andReturn(true).anyTimes();

		reset(mockConnection, mockPool, mockConfig);
		replay(shMockLogger, pshMockLogger);
	}

	/** Test that each method will result in an equivalent bounce on the inner statement (+ test exceptions)
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	@Test
	public void testStandardBounceMethods() throws IllegalArgumentException, IllegalAccessException, InvocationTargetException{
		Set<String> skipTests = new HashSet<String>();
		skipTests.add("close"); 
		skipTests.add("getConnection"); 
		skipTests.add("isClosed");
		skipTests.add("internalClose");
		skipTests.add("trackResultSet");
		skipTests.add("checkClosed");
		skipTests.add("closeStatement"); 
		skipTests.add("closeAndClearResultSetHandles"); 
		skipTests.add("$VRi"); // this only comes into play when code coverage is started. Eclemma bug?

		CommonTestUtils.testStatementBounceMethod(mockConnection, testClass, skipTests, mockClass);
		
	}
	

	/** Test other methods not covered by the standard methods above.
	 * @throws SQLException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws NoSuchFieldException
	 */
	@SuppressWarnings("resource")
	@Test
	public void testRemainingForCoverage() throws SQLException, IllegalArgumentException, SecurityException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException{
		Statement mockStatement = createNiceMock(Statement.class);
		IStatementCache mockCache = createNiceMock(IStatementCache.class);
		expect(mockConnection.getPool()).andReturn(mockPool).anyTimes();
		expect(mockPool.getConfig()).andReturn(mockConfig).anyTimes();
		expect(mockConfig.isStatisticsEnabled()).andReturn(true).anyTimes();
		expect(mockConfig.getQueryExecuteTimeLimitInMs()).andReturn(1L).anyTimes();
		
		replay(mockConnection, mockConfig, mockPool);
		// alternate constructor 
		StatementHandle handle = new StatementHandle(mockStatement, mockConnection, true);

		handle = new StatementHandle(mockStatement, null, mockCache, mockConnection, "testSQL", true);
		
		handle.setLogicallyOpen();
		handle.getConnection();
		
		handle.logicallyClosed.set(true);
		Method method = handle.getClass().getDeclaredMethod("checkClosed");
		method.setAccessible(true);
		try{
			method.invoke(handle);
			Assert.fail("Exception should have been thrown");
		} catch (Throwable e){
		 // do nothing	
		}
		
		mockStatement.close();
		expectLastCall();
		
		
		
		replay(mockStatement, mockCache); 

		handle.close();
		verify(mockStatement);
		reset(mockStatement, mockCache);


		handle.setLogicallyOpen();
		Assert.assertFalse(handle.isClosed());
		
		reset(mockCache);
		mockCache.clear();
		expectLastCall().once();
		replay(mockCache);
		handle.clearCache();
		verify(mockCache);
		
		handle.setOpenStackTrace("foo");
		assertEquals("foo", handle.getOpenStackTrace());
		handle.sql = "foo";
		assertNotNull(handle.toString());
		
		
		testClass.logStatementsEnabled = false;
		testClass.addBatch("");
		testClass.clearBatch();
		
	}
	
	/** temp */
	boolean firstTime = true;
	
	/** Tests query time limit.
	 * @throws SQLException
	 */
	@Test
	public void testQueryTimeLimit() throws SQLException{
		Statement mockStatement = createNiceMock(Statement.class);
		// alternate constructor 
		expect(mockConnection.getPool()).andReturn(mockPool).anyTimes();
		expect(mockPool.getConfig()).andReturn(mockConfig).anyTimes();
		reset(mockConfig);
		ConnectionPartition mockPartition = createNiceMock(ConnectionPartition.class);
		expect(mockPartition.getQueryExecuteTimeLimitinNanoSeconds()).andReturn(1L).anyTimes();
		expect(mockConnection.getOriginatingPartition()).andReturn(mockPartition).anyTimes();
//		expect(mockConfig.getQueryExecuteTimeLimit()).andReturn(1).anyTimes();
		CustomHook hook = new CustomHook();
		expect(mockConfig.getConnectionHook()).andReturn(hook).anyTimes();
		mockStatement.execute((String)EasyMock.anyObject());
		expectLastCall().andAnswer(new IAnswer<Object>() {
			
			@SuppressWarnings("unqualified-field-access")
			public Object answer() throws Throwable {
				if (firstTime){
					Thread.sleep(300);
					firstTime=false;
				}
				return true;
			}
		}).anyTimes();
		replay(mockConnection, mockConfig, mockPool, mockStatement, mockPartition);

		StatementHandle handle = new StatementHandle(mockStatement, mockConnection, true);
		handle.execute("test");
		handle.close();
		assertEquals(1, hook.queryTimeout);
	}

	/**
	 * Coverage.
	 */
	@Test
	public void testGetterSetter(){
		Statement mockStatement = createNiceMock(Statement.class); 
		testClass.setInternalStatement(mockStatement);
		Object obj = new Object();
		testClass.setDebugHandle(obj);
		assertEquals(mockStatement, testClass.getInternalStatement());
		
		assertEquals(obj, testClass.getDebugHandle());
	}
	
	
	/**
	 * @throws SQLException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	@Test
	public void testAssortedCoverage() throws SQLException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
		// using a null batchSQL statement...
		Statement mockStatement = createNiceMock(Statement.class);
		testClass.logStatementsEnabled = false;
		Field field = testClass.getClass().getDeclaredField("internalStatement");
		field.setAccessible(true);
		field.set(testClass, mockStatement);
		expect(mockStatement.executeBatch()).andReturn(null).once();
		replay(mockStatement);
		testClass.executeBatch();
	}
	
	/**
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws SQLException
	 */

}
