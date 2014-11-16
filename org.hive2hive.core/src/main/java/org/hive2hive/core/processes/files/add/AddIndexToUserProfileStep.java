package org.hive2hive.core.processes.files.add;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.PutFailedException;
import org.hive2hive.core.exceptions.VersionForkAfterPutException;
import org.hive2hive.core.model.FileIndex;
import org.hive2hive.core.model.FolderIndex;
import org.hive2hive.core.model.Index;
import org.hive2hive.core.model.versioned.UserProfile;
import org.hive2hive.core.network.data.UserProfileManager;
import org.hive2hive.core.processes.context.AddFileProcessContext;
import org.hive2hive.core.security.HashUtil;
import org.hive2hive.processframework.ProcessStep;
import org.hive2hive.processframework.exceptions.InvalidProcessStateException;
import org.hive2hive.processframework.exceptions.ProcessExecutionException;
import org.hive2hive.processframework.exceptions.ProcessRollbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step adding the new file (node) into the user profile (tree)
 * 
 * @author Nico, Seppi
 */
public class AddIndexToUserProfileStep extends ProcessStep<Void> {

	private static final Logger logger = LoggerFactory.getLogger(AddIndexToUserProfileStep.class);

	private final AddFileProcessContext context;
	private final UserProfileManager profileManager;

	private final int forkLimit = 2;

	public AddIndexToUserProfileStep(AddFileProcessContext context, UserProfileManager profileManager) {
		this.setName(getClass().getName());
		this.context = context;
		this.profileManager = profileManager;
	}

	@Override
	protected Void doExecute() throws InvalidProcessStateException, ProcessExecutionException {
		File file = context.consumeFile();
		File root = context.consumeRoot();

		// pre-calculate the md5 hash because this may take a while
		byte[] md5 = null;
		if (file.isFile()) {
			md5 = calculateHash(file);
		}

		// update the user profile where adding the file
		int forkCounter = 0;
		int forkWaitTime = new Random().nextInt(1000) + 500;
		while (true) {
			UserProfile userProfile;
			try {
				userProfile = profileManager.getUserProfile(getID(), true);
			} catch (GetFailedException ex) {
				throw new ProcessExecutionException(this, ex);
			}

			// find the parent node using the relative path to navigate there
			FolderIndex parentNode = (FolderIndex) userProfile.getFileByPath(file.getParentFile(), root);

			// validate the write protection
			if (!parentNode.canWrite()) {
				throw new ProcessExecutionException(this, "The directory is write protected (and we don't have the keys).");
			}

			// create a file tree node in the user profile
			if (file.isDirectory()) {
				FolderIndex folderIndex = new FolderIndex(parentNode, context.consumeMetaFileEncryptionKeys(),
						file.getName());
				context.provideIndex(folderIndex);
			} else {
				FileIndex fileIndex = new FileIndex(parentNode, context.consumeMetaFileEncryptionKeys(), file.getName(), md5);
				context.provideIndex(fileIndex);
			}

			try {
				// put the updated user profile
				profileManager.readyToPut(userProfile, getID());

				// set flag, that an update has been made (for roll back)
				setRequiresRollback(true);
			} catch (VersionForkAfterPutException e) {
				if (forkCounter++ > forkLimit) {
					logger.warn("Ignoring fork after {} rejects and retries.", forkCounter);
				} else {
					logger.warn("Version fork after put detected. Rejecting and retrying put.");

					// exponential back off waiting
					try {
						Thread.sleep(forkWaitTime);
					} catch (InterruptedException e1) {
						// ignore
					}
					forkWaitTime = forkWaitTime * 2;

					// retry update of user profile
					continue;
				}
			} catch (PutFailedException ex) {
				throw new ProcessExecutionException(this, ex);
			}

			break;
		}
		return null;
	}

	private byte[] calculateHash(File file) throws ProcessExecutionException {
		try {
			return HashUtil.hash(file);
		} catch (IOException ex) {
			logger.error("Creating MD5 hash of file '{}' was not possible.", file.getName(), ex);
			throw new ProcessExecutionException(this, ex, String.format("Could not add file '%s' to the user profile.",
					file.getName()));
		}
	}

	@Override
	protected Void doRollback() throws InvalidProcessStateException, ProcessRollbackException {
		File file = context.consumeFile();
		File root = context.consumeRoot();

		// remove the file from the user profile
		int forkCounter = 0;
		int forkWaitTime = new Random().nextInt(1000) + 500;
		while (true) {
			UserProfile userProfile;
			try {
				userProfile = profileManager.getUserProfile(getID(), true);
			} catch (GetFailedException ex) {
				throw new ProcessRollbackException(this, ex, "Couldn't load user profile for redo.");
			}

			// find the parent and child node
			FolderIndex parentNode = (FolderIndex) userProfile.getFileByPath(file.getParentFile(), root);
			Index childNode = parentNode.getChildByName(file.getName());

			// remove newly added child node
			parentNode.removeChild(childNode);

			try {
				// put the user profile
				profileManager.readyToPut(userProfile, getID());

				// adapt flag
				setRequiresRollback(false);
			} catch (VersionForkAfterPutException e) {
				if (forkCounter++ > forkLimit) {
					logger.warn("Ignoring fork after {} rejects and retries.", forkCounter);
				} else {
					logger.warn("Version fork after put detected. Rejecting and retrying put.");

					// exponential back off waiting
					try {
						Thread.sleep(forkWaitTime);
					} catch (InterruptedException e1) {
						// ignore
					}
					forkWaitTime = forkWaitTime * 2;

					// retry update of user profile
					continue;
				}
			} catch (PutFailedException ex) {
				throw new ProcessRollbackException(this, ex, "Couldn't redo put of user profile.");
			}
			break;
		}
		
		return null;
	}
}
