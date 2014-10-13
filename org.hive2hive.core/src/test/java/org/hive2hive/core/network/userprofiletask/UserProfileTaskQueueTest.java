package org.hive2hive.core.network.userprofiletask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.H2HSession;
import org.hive2hive.core.api.configs.FileConfiguration;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.PutFailedException;
import org.hive2hive.core.file.FileTestUtil;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.NetworkTestUtil;
import org.hive2hive.core.network.data.PublicKeyManager;
import org.hive2hive.core.network.data.UserProfileManager;
import org.hive2hive.core.network.data.parameters.Parameters;
import org.hive2hive.core.processes.common.userprofiletask.GetUserProfileTaskStep;
import org.hive2hive.core.processes.common.userprofiletask.PutUserProfileTaskStep;
import org.hive2hive.core.processes.common.userprofiletask.RemoveUserProfileTaskStep;
import org.hive2hive.core.processes.context.interfaces.IUserProfileTaskContext;
import org.hive2hive.core.processes.login.SessionParameters;
import org.hive2hive.core.security.EncryptionUtil;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.processframework.RollbackReason;
import org.hive2hive.processframework.concretes.SequentialProcess;
import org.hive2hive.processframework.exceptions.InvalidProcessStateException;
import org.hive2hive.processframework.exceptions.ProcessExecutionException;
import org.hive2hive.processframework.util.TestExecutionUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Seppi
 */
public class UserProfileTaskQueueTest extends H2HJUnitTest {

	private static final IFileConfiguration config = FileConfiguration.createDefault();
	private static ArrayList<NetworkManager> network;
	private static File root;
	private static final int networkSize = 10;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = UserProfileTaskQueueTest.class;
		beforeClass();
		network = NetworkTestUtil.createNetwork(networkSize);
		root = FileTestUtil.getTempDirectory();
	}

	@Test
	public void testPut() throws NoPeerConnectionException, InvalidProcessStateException {
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();
		TestUserProfileTask userProfileTask = new TestUserProfileTask();
		KeyPair key = EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS);
		NetworkManager node = NetworkTestUtil.getRandomNode(network);

		TestPutUserProfileTaskStep putStep = new TestPutUserProfileTaskStep(credentials.getUserId(), userProfileTask,
				key.getPublic(), node);
		TestExecutionUtil.executeProcessTillSucceded(putStep);

		// check if user profile task has been put into network
		Parameters parameters = new Parameters().setLocationKey(credentials.getUserId())
				.setDomainKey(H2HConstants.USER_PROFILE_TASK_DOMAIN).setContentKey(userProfileTask.getContentKey());
		assertNotNull(node.getDataManager().get(parameters));

		// manually trigger roll back
		putStep.cancel(new RollbackReason("Testing rollback."));

		// check if user profile task has been removed from network
		assertNull(node.getDataManager().get(parameters));
	}

	@Test
	public void testPutGet() throws IOException, NoPeerConnectionException, InvalidProcessStateException {
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();
		TestUserProfileTask userProfileTask = new TestUserProfileTask();
		KeyPair key = EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS);
		NetworkManager node = NetworkTestUtil.getRandomNode(network);
		PublicKeyManager publicKeyManager = new PublicKeyManager(credentials.getUserId(), key, node.getDataManager());
		UserProfileManager userProfileManager = new UserProfileManager(node.getDataManager(), credentials);
		SessionParameters params = new SessionParameters(root.toPath(), config);
		params.setKeyManager(publicKeyManager);
		params.setUserProfileManager(userProfileManager);
		node.setSession(new H2HSession(params));

		SimpleGetUserProfileTaskContext context = new SimpleGetUserProfileTaskContext();

		SequentialProcess process = new SequentialProcess();
		process.add(new TestPutUserProfileTaskStep(credentials.getUserId(), userProfileTask, key.getPublic(), node));
		process.add(new GetUserProfileTaskStep(context, node));

		TestExecutionUtil.executeProcessTillSucceded(process);

		// check if user profile task has been put into network
		Parameters parameters = new Parameters().setLocationKey(credentials.getUserId())
				.setDomainKey(H2HConstants.USER_PROFILE_TASK_DOMAIN).setContentKey(userProfileTask.getContentKey());
		assertNotNull(node.getDataManager().get(parameters));
		// check if context has been initialized
		assertNotNull(context.consumeUserProfileTask());
		assertEquals(userProfileTask.getId(), ((TestUserProfileTask) context.consumeUserProfileTask()).getId());

		// manually trigger roll back
		process.cancel(new RollbackReason("Testing rollback."));

		// check if context has been cleaned up
		assertNull(context.consumeUserProfileTask());
		// check if user profile task has been removed from network
		assertNull(node.getDataManager().get(parameters));
	}

	@Test
	public void testPutGetRemove() throws NoPeerConnectionException, IOException, InvalidProcessStateException {
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();
		TestUserProfileTask userProfileTask = new TestUserProfileTask();
		KeyPair key = EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS);
		NetworkManager node = NetworkTestUtil.getRandomNode(network);
		PublicKeyManager publicKeyManager = new PublicKeyManager(credentials.getUserId(), key, node.getDataManager());
		UserProfileManager userProfileManager = new UserProfileManager(node.getDataManager(), credentials);
		SessionParameters params = new SessionParameters(root.toPath(), config);
		params.setKeyManager(publicKeyManager);
		params.setUserProfileManager(userProfileManager);
		node.setSession(new H2HSession(params));

		SimpleGetUserProfileTaskContext context = new SimpleGetUserProfileTaskContext();

		SequentialProcess process = new SequentialProcess();
		process.add(new TestPutUserProfileTaskStep(credentials.getUserId(), userProfileTask, key.getPublic(), node));
		process.add(new GetUserProfileTaskStep(context, node));
		process.add(new RemoveUserProfileTaskStep(context, node));

		TestExecutionUtil.executeProcessTillSucceded(process);

		Parameters parameters = new Parameters().setLocationKey(credentials.getUserId())
				.setDomainKey(H2HConstants.USER_PROFILE_TASK_DOMAIN).setContentKey(userProfileTask.getContentKey());
		assertNull(node.getDataManager().get(parameters));

		// manually trigger roll back
		process.cancel(new RollbackReason("Testing rollback."));

		// check if context has been cleaned up
		assertNull(context.consumeUserProfileTask());
		// check if user profile task has been removed from network
		assertNull(node.getDataManager().get(parameters));
	}

	@Test
	public void testCorrectOrder() throws DataLengthException, InvalidKeyException, IllegalStateException,
			InvalidCipherTextException, IllegalBlockSizeException, BadPaddingException, InterruptedException, IOException,
			NoPeerConnectionException {
		UserCredentials credentials = NetworkTestUtil.generateRandomCredentials();
		NetworkManager node = NetworkTestUtil.getRandomNode(network);
		KeyPair key = EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS);
		PublicKeyManager publicKeyManager = new PublicKeyManager(credentials.getUserId(), key, node.getDataManager());
		UserProfileManager userProfileManager = new UserProfileManager(node.getDataManager(), credentials);
		SessionParameters params = new SessionParameters(root.toPath(), config);
		params.setKeyManager(publicKeyManager);
		params.setUserProfileManager(userProfileManager);
		node.setSession(new H2HSession(params));

		// create some tasks
		List<TestUserProfileTask> tasks = new ArrayList<TestUserProfileTask>();
		for (int i = 0; i < 5; i++) {
			TestUserProfileTask task = new TestUserProfileTask();
			tasks.add(task);
			// to guarantee different time stamps
			Thread.sleep(10);
		}

		// shuffle tasks to change the order
		List<TestUserProfileTask> shuffledTasks = new ArrayList<TestUserProfileTask>(tasks);
		Collections.shuffle(shuffledTasks);
		for (TestUserProfileTask task : shuffledTasks) {
			TestPutUserProfileTaskStep putStep = new TestPutUserProfileTaskStep(credentials.getUserId(), task,
					key.getPublic(), node);
			TestExecutionUtil.executeProcessTillSucceded(putStep);
		}

		// fetch task from network, respectively the implicit queue
		List<TestUserProfileTask> downloadedTasks = new ArrayList<TestUserProfileTask>();
		SimpleGetUserProfileTaskContext context = new SimpleGetUserProfileTaskContext();
		while (true) {
			GetUserProfileTaskStep getStep = new GetUserProfileTaskStep(context, node);
			TestExecutionUtil.executeProcessTillSucceded(getStep);
			if (context.consumeUserProfileTask() != null) {
				TestUserProfileTask task = (TestUserProfileTask) context.consumeUserProfileTask();
				downloadedTasks.add(task);
				// remove successfully get user profile tasks
				RemoveUserProfileTaskStep removeStep = new RemoveUserProfileTaskStep(context, node);
				TestExecutionUtil.executeProcessTillSucceded(removeStep);
			} else {
				break;
			}
		}

		// order of fetched tasks should be like the initial one
		assertEquals(tasks.size(), downloadedTasks.size());
		for (int i = 0; i < tasks.size(); i++) {
			assertEquals(tasks.get(i).getId(), downloadedTasks.get(i).getId());
		}
	}

	@AfterClass
	public static void cleanAfterClass() throws IOException {
		NetworkTestUtil.shutdownNetwork(network);
		afterClass();
		FileUtils.deleteDirectory(root);
	}

	private class SimpleGetUserProfileTaskContext implements IUserProfileTaskContext {

		private UserProfileTask userProfileTask;

		@Override
		public void provideUserProfileTask(UserProfileTask profileTask) {
			userProfileTask = profileTask;
		}

		@Override
		public UserProfileTask consumeUserProfileTask() {
			return userProfileTask;
		}

	};

	private class TestPutUserProfileTaskStep extends PutUserProfileTaskStep {

		private final String userId;
		private final TestUserProfileTask userProfileTask;
		private final PublicKey publicKey;

		public TestPutUserProfileTaskStep(String userId, TestUserProfileTask userProfileTask, PublicKey publicKey,
				NetworkManager networkManager) {
			super(networkManager);
			this.userId = userId;
			this.userProfileTask = userProfileTask;
			this.publicKey = publicKey;
		}

		@Override
		protected void doExecute() throws InvalidProcessStateException, ProcessExecutionException {
			try {
				put(userId, userProfileTask, publicKey);
			} catch (PutFailedException e) {
				throw new ProcessExecutionException(e);
			}
		}

	}

}
